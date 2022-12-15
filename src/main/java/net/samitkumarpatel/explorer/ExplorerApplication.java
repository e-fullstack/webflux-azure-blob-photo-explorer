package net.samitkumarpatel.explorer;

import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.models.ParallelTransferOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ExplorerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExplorerApplication.class, args);
	}
}

@Configuration
@RequiredArgsConstructor
class Routers {
	private final Services services;
	@Bean
	public RouterFunction route() {
		return RouterFunctions.route()
				.GET("/album", request -> ok().body("", String.class))
				.PUT("/album", request -> ok().body("", String.class))
				.POST("/album", request -> {
					var name = request.queryParam("name").orElse("default");
					return ok().body(services.albumCreate(name), String.class);
				})
				.POST("/album/{name}/uploadOne", request -> {
					var albumName = request.pathVariable("name");
					return request.multipartData()
							.flatMap(stringPartMultiValueMap -> {
								// This will just support one attachment, If need more this need to be extend
								var stringPartMap= stringPartMultiValueMap.toSingleValueMap();
								var filePart = (FilePart) stringPartMap.get("file");
								return services.upload(albumName, filePart.filename(), filePart.content());
							}).then(ok().body(Mono.just("Uploaded"), String.class));
				})
				.POST("/album/{name}/uploadMany", request -> {
					var albumName = request.pathVariable("name");
					return request.multipartData().flatMap(stringPartMultiValueMap -> {
						var stringPartMap= stringPartMultiValueMap.toSingleValueMap();
						var filePart = (FilePart)stringPartMap.get("files");
						return Mono.just(filePart.filename());
					}).flatMap(v -> ok().body(Mono.just(v), String.class));
				})
				.build();
	}
}

@Service
@Slf4j
@RequiredArgsConstructor
class Services {
	private final BlobServiceAsyncClient blobServiceAsyncClient;
	public Mono<String> albumCreate(String name) {
		var purifiedName = name.replaceAll("\\W", "-").replaceAll("_", "-");
		log.info("albumCreate Original name {} converted to Purified Name {}", name, purifiedName);
		return blobServiceAsyncClient
				.createBlobContainerIfNotExists(purifiedName)
				.doOnError(e -> log.info("albumCreation ERROR {}", e.getMessage()))
				.flatMap(blobContainerAsyncClient -> Mono.just("SUCCESS"))
				.doOnSuccess(r -> log.info("albumCreation SUCCESS {}", purifiedName));
	}

	public Mono<String> upload(String albumName, String fileName, Flux<DataBuffer> content) {
		//FluxUtil.toFluxByteBuffer(new ByteArrayInputStream(content)) if content is byte[]

		return blobServiceAsyncClient
				.getBlobContainerAsyncClient(albumName)
				.getBlobAsyncClient(fileName)
				.upload(content.map(DataBuffer::toByteBuffer), new ParallelTransferOptions().setBlockSizeLong(2 * 1024L * 1024L).setMaxConcurrency(5))
				.doOnError(e -> log.error("Upload ERROR {}", e.getMessage()))
				.doOnSuccess(r -> log.info("Upload SUCCESS {}", r.getVersionId()))
				.flatMap(blockBlobItem -> Mono.just("DONE"));
	}
}