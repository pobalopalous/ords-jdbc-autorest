package blog.peterobrien.jdbc.autorest;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import oracle.dbtools.plugin.api.di.annotations.ApplicationScoped;
import oracle.dbtools.plugin.api.di.annotations.Provides;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Provides
public class OpenAPICache {

    private final LoadingCache<String, OpenAPI> cache;

    public OpenAPICache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000) // Maximum number of entries
                .expireAfterWrite(5, TimeUnit.MINUTES) // Expire entries after 5 minutes
                .build(this::loadOpenAPI);
    }

    /**
     * Loads an OpenAPI document from a file path.
     *
     * @param filePath the path to the OpenAPI document
     * @return the parsed OpenAPI object
     */
    private OpenAPI loadOpenAPI(String filePath) {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(filePath, null, null);
        if (result.getOpenAPI() != null) {
            return result.getOpenAPI();
        } else {
            throw new RuntimeException("Failed to load OpenAPI document: " + filePath);
        }
    }

    /**
     * Retrieves the OpenAPI document from the cache.
     *
     * @param filePath the path to the OpenAPI document
     * @return the cached or newly loaded OpenAPI object
     */
    public OpenAPI getOpenAPIDocument(String filePath) {
        return cache.get(filePath);
    }
}
