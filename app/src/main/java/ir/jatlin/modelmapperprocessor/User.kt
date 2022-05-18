package ir.jatlin.modelmapperprocessor


import ir.jatlin.annotations.Mappable
import ir.jatlin.asUser


@Mappable(name = "User")
data class NetworkUser(
    val name: String,
    var age: Int = 1,
    val code: String,
    var comments: List<String>,
    val friends: List<String>
)

fun main() {

    val networkUser = NetworkUser(
        name = "username",
        code = "",
        comments = emptyList(),
        friends = emptyList()
    )
    print(networkUser.asUser())

}