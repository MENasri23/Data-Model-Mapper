package ir.jatlin.mapper.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import ir.jatlin.annotations.Mappable
import java.io.OutputStream

class ModelMapperProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var targetClassName: String? = null
    private var originClassName: String? = null
    private var properties = mutableListOf<String>()

    companion object {
        private const val MAPPABLE_ANNOTATION_SHORT_NAME = "Mappable"
    }

    private val mappableAnnotationName = Mappable::class.qualifiedName


    operator fun OutputStream.plusAssign(str: String) =
        write(str.toByteArray())


    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver
            .getSymbolsWithAnnotation("${Mappable::class.qualifiedName}")
            .filter { it is KSClassDeclaration && it.validate() }

        if (!symbols.iterator().hasNext()) return emptyList()


        val file: OutputStream = codeGenerator.createNewFile(
            dependencies = Dependencies(
                false,
                *resolver.getAllFiles().toList().toTypedArray()
            ),
            packageName = "ir.jatlin",
            fileName = "ModelMappers"
        )
        file += "package ir.jatlin\n\n"

        for (symbol in symbols) {
            symbol.accept(MappableVisitor(file), Unit)
        }

        file += "\n\n"

        createMapperExtension(file)

        file.close()
        // return invalid symbols
        return symbols.filterNot { it.validate() }.toList()
    }

    private fun createMapperExtension(file: OutputStream) {
        if (originClassName == null || targetClassName == null) {
            logger.error("Class name must be non null")
            return
        }
        file += "fun ${originClassName}.as${targetClassName}() = $targetClassName(\n"
        properties.forEach { propName ->
            file += "$propName = $propName,\n"
        }
        file += ")"
    }


    inner class MappableVisitor(private val file: OutputStream) : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can by annotated with $mappableAnnotationName",
                    classDeclaration
                )
                return
            }
            originClassName = classDeclaration.qualifiedName?.asString()

            val annotation = classDeclaration.annotations.first {
                it.shortName.asString() == MAPPABLE_ANNOTATION_SHORT_NAME
            }
            val arguments = annotation.arguments
            val mapNameArg = arguments.firstOrNull { argument ->
                argument.name?.asString() == "name"
            }
            val targetName = (mapNameArg?.value as? String).let {
                if (it.isNullOrBlank()) {
                    "Mapped${classDeclaration.simpleName.asString()}"
                } else it
            }
            targetClassName = targetName

            val properties = classDeclaration.getAllProperties().filter { it.validate() }

            file += "data class $targetName(\n"
            for (property in properties) {
                property.accept(this, Unit)
            }
            file += ")"
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            val isNotAccessible =
                property.modifiers
                    .any { it == Modifier.PRIVATE || it == Modifier.PROTECTED }
            if (isNotAccessible) {
                return
            }

            properties.add(property.simpleName.asString())
            val propertyText = getPropertyName(property)
            val propertyType = getPropertyType(property)

            file += (propertyText + propertyType)
            file += ",\n"

        }

        private fun getPropertyName(property: KSPropertyDeclaration) =
            buildString {
                append('\t')
                // e.g: private/protected ..
                property.modifiers.forEach { append(it.name.lowercase()).append(' ') }
                append(if (property.isMutable) "var " else "val ")
                append(property.simpleName.asString()).append(": ")

                // for now: 'modifiers val/var propertyName: '
                val typeResolved = property.type.resolve()
                append(typeResolved.declaration.qualifiedName?.asString() ?: "<ERROR>")

            }

        private fun getPropertyType(property: KSPropertyDeclaration) = buildString {
            val typeArgs = property.type.element?.typeArguments
            if (!typeArgs.isNullOrEmpty()) {
                append('<')
                append(
                    typeArgs.joinToString(", ") {
                        val type = it.type?.resolve() ?: return@joinToString ""
                        "${it.variance.label} ${type.declaration.qualifiedName?.asString() ?: "ERROR"}" +
                                if (type.isMarkedNullable) "?" else ""
                    }
                )
                append('>')
            }

        }

    }


}