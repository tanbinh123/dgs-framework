package com.netflix.graphql.dgs.autoconfig

import com.jayway.jsonpath.PathNotFoundException
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.testcomponents.HelloDatFetcherConfig
import com.netflix.graphql.dgs.exceptions.QueryException
import graphql.ExecutionResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class QueryExecutorTest {
    private val context = WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(DgsAutoConfiguration::class.java))!!

    @Test
    fun query() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("{ hello }", "data.hello")
            }.isEqualTo("Hello!")
        }
    }

    @Test
    fun queryWithArgument() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("{ hello(name: \"DGS\") }", "data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryWithVariables() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndExtractJsonPath<String>("query(\$name: String) {  hello(name: \$name) }", "data.hello", mapOf(Pair("name", "DGS")))
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryWithQueryError() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThrows<QueryException> {
                ctx.getBean(DgsQueryExecutor::class.java).executeAndExtractJsonPath<String>("{unknown}", "data.unknown")
            }
        }
    }

    @Test
    fun queryWithJsonPathError() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThrows<PathNotFoundException> {
                ctx.getBean(DgsQueryExecutor::class.java).executeAndExtractJsonPath<String>("{hello}", "data.unknown")
            }
        }
    }

    @Test
    fun queryDocumentWithArgument() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndGetDocumentContext("{ hello(name: \"DGS\") }").read<String>("data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryDocumentWithVariables() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.executeAndGetDocumentContext("query(\$name: String) {  hello(name: \$name) }", mapOf(Pair("name", "DGS"))).read<String>("data.hello")
            }.isEqualTo("Hello, DGS!")
        }
    }

    @Test
    fun queryDocumentWithError() {

        val error : QueryException = assertThrows {
            context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
                assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                    it.executeAndGetDocumentContext("{unknown }")
                }
            }
        }

        assertThat(error.errors.size).isEqualTo(1)
        assertThat(error.errors[0].message).isEqualTo("Validation error of type FieldUndefined: Field 'unknown' in type 'Query' is undefined @ 'unknown'")
    }

    @Test
    fun queryBasicExecute() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.execute("{hello}").isDataPresent
            }.isEqualTo(true)
        }
    }

    @Test
    fun queryBasicExecuteWithError() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                it.execute("{unknown}").errors?.size
            }.isEqualTo(1)
        }
    }

    @Test
    fun queryReturnsNullForField() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val execute: ExecutionResult = it.execute("{withNullableNull}")
                tuple(execute.getData<Map<String, String>>()?.get("withNulableNull"), execute.errors?.size)
            }.isEqualTo(tuple(null, 0))
        }
    }

    @Test
    fun queryReturnsErrorForNonNullableField() {
        context.withUserConfiguration(HelloDatFetcherConfig::class.java).run { ctx ->
            assertThat(ctx).getBean(DgsQueryExecutor::class.java).extracting {
                val execute = it.execute("{withNonNullableNull}")
                tuple(execute.getData<Map<String, String>>()?.get("withNonNullableNull"), execute.errors?.size)
            }.isEqualTo(tuple(null, 1))
        }
    }
}