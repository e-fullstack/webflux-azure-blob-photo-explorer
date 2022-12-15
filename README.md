[api details](https://learn.microsoft.com/en-us/java/api/com.azure.storage.blob.blobasyncclient?view=azure-java-stable)

[spring boot azure storage support](https://spring.io/projects/spring-cloud-azure)

For BlobServiceAsyncClient springboot need this property 
```yaml
spring:
    cloud:
        azure:
            storage:
                account-name: xxxxxxxxxxxxxxxxx
                account-key: xxxxxxxxxxxxxxxxxx
```
or 
```java
BlobServiceAsyncClient blobServiceAsyncClient =  new BlobServiceClientBuilder()
				.endpoint(endpoint)
				.connectionString(connectionString)
				.buildAsyncClient();
```