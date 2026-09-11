import java.util.regex.Pattern

fun main() {
    val text = """
        John Doe: Hello everyone, thanks for joining.
        Jane Smith: Thanks for having me.
        John Doe: So, let's get started.
    """.trimIndent()
    
    val regex = Regex("(?m)^([A-Za-z0-9 ]{2,20}):")
    val matches = regex.findAll(text)
    for (match in matches) {
        println("Found speaker: ${match.groupValues[1]} at ${match.range}")
    }
}
