package example

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client("\${users.url}")
interface UsersClient {
    @Get(value = "/users/{id}", consumes = [MediaType.TEXT_PLAIN])
    fun user(id: String): String
}
