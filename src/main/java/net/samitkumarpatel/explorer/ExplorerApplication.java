package net.samitkumarpatel.explorer;

import com.azure.core.http.rest.PagedFlux;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.ParallelTransferOptions;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ExplorerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExplorerApplication.class, args);
	}
}

@Configuration
@Slf4j
@RequiredArgsConstructor
class Routers {
	@Bean
	public RouterFunction route(Handlers handlers) {
		return RouterFunctions.route()
				.path("/album", builder -> builder
						.GET("", handlers::albumAll)
						.GET("/v2", handlers::albumAllV2)
						.POST("", handlers::createAlbum)
						.PUT("", request -> ok().body("", String.class))
						.DELETE("", request -> ok().body("", String.class))
						.path("/{name}/photo", builder1 -> builder1
								.GET("", handlers::albumContent)
								.POST("/upload", handlers::uploadOne)
								.POST("/upload-many", handlers::uploadMany)
						)
				)
				.build();
	}
}

@RestController
@Slf4j
@RequiredArgsConstructor
class RestControllers {
	private final Services services;
	@PostMapping("v2/album/photo/{name}/upload-many")
	public Mono<Void> upload(@PathVariable("name") String name, @RequestPart("files") Flux<FilePart> partFlux) {
		return partFlux
				.doOnNext(fp -> log.info("FileName {}", fp.filename()))
				.flatMap(fp -> services.upload(name, fp.filename(), fp.content()))
				.then();
	}
}

@Configuration
@Slf4j
@RequiredArgsConstructor
class Handlers {
	private final Services services;

	public Mono<ServerResponse> createAlbum(ServerRequest request) {
		var name = request.queryParam("name").orElse("default");
		return ok().body(services.albumCreate(name), String.class);
	}
	public Mono<ServerResponse> albumAll(ServerRequest request) {
		return ok().body(services.albumAll(), List.class);
	}

	public Mono<ServerResponse> albumAllV2(ServerRequest request) {
		return ok().body(services.albumAllV2(), PagedFlux.class);
	}

	public Mono<ServerResponse> albumContent(ServerRequest request) {
		return ok().body(services.albumContent(request.pathVariable("name")), List.class);
	}
	public Mono<ServerResponse> uploadOne(ServerRequest request) {
		var albumName = request.pathVariable("name");
		return request.multipartData()
				.flatMap(stringPartMultiValueMap -> {
					// This will just support one attachment, If need more this need to be extend
					var stringPartMap= stringPartMultiValueMap.toSingleValueMap();
					var filePart = (FilePart) stringPartMap.get("file");
					return services.upload(albumName, filePart.filename(), filePart.content());
				}).then(ok().body(Mono.just("Uploaded"), String.class));
	}
	public Mono<ServerResponse> uploadMany(ServerRequest request) {
		var albumName = request.pathVariable("name");
		return request.multipartData()
				.map(stringPartMultiValueMap -> stringPartMultiValueMap.get("files"))
				.flatMapMany(Flux::fromIterable)
				.cast(FilePart.class)
				.flatMap(filePart -> {
					log.info("{} file Processed", filePart.filename());
					return services.upload(albumName, filePart.filename(), filePart.content());
				}).then(ok().body(Mono.just("SUCCESS"), String.class));
	}
}
@Service
@Slf4j
@RequiredArgsConstructor
class Services {
	private final BlobServiceAsyncClient blobServiceAsyncClient;
	public Mono<String> albumCreate(String name) {
		var purifiedName = name.replaceAll("\\W", "-").replaceAll("[^a-zA-Z0-9>]", "-");
		log.info("albumCreate Original name {} converted to Purified Name {}", name, purifiedName);
		return blobServiceAsyncClient
				.createBlobContainerIfNotExists(purifiedName)
				.doOnError(e -> log.info("albumCreation ERROR {}", e.getMessage()))
				.flatMap(blobContainerAsyncClient -> Mono.just("SUCCESS"))
				.doOnSuccess(r -> log.info("albumCreation SUCCESS {}", purifiedName));
	}

	public Mono<List<Album>> albumAll() {
		return blobServiceAsyncClient
				.listBlobContainers()
				.map(blobContainerItem -> Album.builder().name(blobContainerItem.getName()).lastModified(blobContainerItem.getProperties().getLastModified().format(DateTimeFormatter.ISO_LOCAL_DATE)).build())
				.collectList();
	}

	public PagedFlux<BlobContainerItem> albumAllV2() {
		return blobServiceAsyncClient.listBlobContainers();
	}
	public Mono<List<AlbumContent>> albumContent(String albumName) {
		return blobServiceAsyncClient
				.getBlobContainerAsyncClient(albumName)
				.listBlobs()
				.map(blobItem -> AlbumContent.builder().name(blobItem.getName()).lastModified(blobItem.getProperties().getLastModified().format(DateTimeFormatter.ISO_LOCAL_DATE)).size(blobItem.getProperties().getContentLength().toString()).build()).collectList();
	}

	public Mono<String> upload(String albumName, String fileName, Flux<DataBuffer> content) {
		//FluxUtil.toFluxByteBuffer(new ByteArrayInputStream(content)) if content is byte[]
		log.info("service::upload albumName {} , fileName {}", albumName, fileName);
		return blobServiceAsyncClient
				.getBlobContainerAsyncClient(albumName)
				.getBlobAsyncClient(fileName)
				.upload(content.map(DataBuffer::toByteBuffer), new ParallelTransferOptions().setBlockSizeLong(2 * 1024L * 1024L).setMaxConcurrency(5), true)
				.doOnError(e -> log.error("Upload ERROR {}", e.getMessage()))
				.doOnSuccess(r -> log.info("Upload SUCCESS {}", r.getVersionId()))
				.flatMap(blockBlobItem -> Mono.just("DONE"))
				.onErrorResume(e -> Mono.error(e));
	}
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Album {
	private String name;
	private String lastModified;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AlbumContent {
	private String name;
	private String lastModified;
	private String size;
}