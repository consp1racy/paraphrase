// Copyright Square, Inc.
package app.cash.gingham.plugin

import app.cash.gingham.model.FormattedResource
import app.cash.gingham.model.NamedArgFormattedResource
import app.cash.gingham.model.NumberedArgFormattedResource
import app.cash.gingham.plugin.model.TokenizedResource
import app.cash.gingham.plugin.model.TokenizedResource.Token
import app.cash.gingham.plugin.model.TokenizedResource.Token.NamedToken
import app.cash.gingham.plugin.model.TokenizedResource.Token.NumberedToken
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock

private const val GINGHAM_PACKAGE = "app.cash.gingham"

private val FORMATTED_RESOURCES =
  ClassName(packageName = GINGHAM_PACKAGE, "FormattedResources")

/**
 * Writes the given tokenized resources to a Kotlin source file.
 */
internal fun writeResources(
  packageName: String,
  tokenizedResources: List<TokenizedResource>
): FileSpec {
  val packageStringsType = ClassName(packageName = packageName, "R", "string")
  return FileSpec.builder(packageName = packageName, fileName = "FormattedResources")
    .addImport(packageName = packageName, "R")
    .addType(
      TypeSpec.objectBuilder("FormattedResources")
        .apply {
          tokenizedResources.forEach { tokenizedResource ->
            addFunction(tokenizedResource.toFunSpec(packageStringsType))
          }
        }
        .build()
    )
    .build()
}

private fun TokenizedResource.toFunSpec(packageStringsType: TypeName): FunSpec {
  val hasNumberedArgs = tokens.any { it is NumberedToken }
  val parameters = tokens.map { it.toParameterSpec() }
  return FunSpec.builder(name)
    .apply { if (description != null) addKdoc(description) }
    .receiver(FORMATTED_RESOURCES)
    .apply { parameters.forEach { addParameter(it) } }
    .returns(FormattedResource::class.java)
    .apply {
      if (hasNumberedArgs) {
        addStatement("val arguments = listOf(%L)", parameters.joinToString { it.name })
        addCode(
          buildCodeBlock {
            add("return %T(⇥\n", NumberedArgFormattedResource::class.java)
            addStatement("id = %T.%L,", packageStringsType, name)
            addStatement("arguments = arguments")
            add("⇤)\n")
          }
        )
      } else {
        addStatement(
          "val arguments = mapOf(%L)",
          parameters.joinToString { "\"${it.name}\" to ${it.name}" }
        )
        addCode(
          buildCodeBlock {
            add("return %T(⇥\n", NamedArgFormattedResource::class.java)
            addStatement("id = %T.%L,", packageStringsType, name)
            addStatement("arguments = arguments")
            add("⇤)\n")
          }
        )
      }
    }
    .build()
}

private fun Token.toParameterSpec(): ParameterSpec =
  ParameterSpec(
    name = when (this) {
      is NamedToken -> name
      is NumberedToken -> "arg$number"
    },
    type = type.asClassName()
  )
