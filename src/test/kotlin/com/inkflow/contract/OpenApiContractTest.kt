package com.inkflow.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * OpenAPI 문서의 계약(필수 섹션/경로/스키마)을 검증한다.
 */
class OpenApiContractTest {
    private val openApiPath: Path = Paths.get("docs", "api", "openapi.yaml")
    private val yaml = Yaml()

    /**
     * OpenAPI 문서의 핵심 섹션이 누락되지 않았는지 확인한다.
     */
    @Test
    fun openApiSpec_containsRequiredSections() {
        val root = loadOpenApi()

        assertEquals("3.0.3", root["openapi"])
        val info = requireMap(root, "info")
        assertNotNull(info["title"])
        assertNotNull(info["version"])
        assertNotNull(root["paths"])
        assertNotNull(root["components"])
    }

    /**
     * 핵심 API 엔드포인트가 명세에 포함되어 있는지 검증한다.
     */
    @Test
    fun openApiSpec_definesKeyEndpoints() {
        val root = loadOpenApi()
        val paths = requireMap(root, "paths")

        val expectedPaths = listOf(
            "/uploads",
            "/uploads/{uploadId}/complete",
            "/episodes/{episodeId}/submit",
            "/ops/dlq"
        )

        expectedPaths.forEach { path ->
            assertTrue(paths.containsKey(path), "OpenAPI 문서에 경로가 누락되었습니다: $path")
        }
    }

    /**
     * 표준 응답 envelope 스키마가 정의되어 있는지 확인한다.
     */
    @Test
    fun openApiSpec_definesEnvelopeSchemas() {
        val root = loadOpenApi()
        val components = requireMap(root, "components")
        val schemas = requireMap(components, "schemas")

        val expectedSchemas = listOf(
            "UploadInitEnvelope",
            "UploadCompleteEnvelope",
            "WorkflowStateEnvelope",
            "DlqMessagePageEnvelope",
            "DlqMessageDetailEnvelope",
            "DlqReprocessEnvelope"
        )

        expectedSchemas.forEach { schema ->
            assertTrue(schemas.containsKey(schema), "OpenAPI 문서에 스키마가 누락되었습니다: $schema")
        }

        val envelopeSchema = requireMap(schemas, "UploadInitEnvelope")
        val requiredFields = requireList(envelopeSchema, "required")
        assertTrue(
            requiredFields.containsAll(listOf("requestId", "code", "message", "data")),
            "표준 응답 envelope 필드가 누락되었습니다."
        )
    }

    /**
     * OpenAPI 문서를 로드해 루트 맵으로 반환한다.
     */
    private fun loadOpenApi(): Map<String, Any> {
        assertTrue(Files.exists(openApiPath), "OpenAPI 문서가 존재하지 않습니다: $openApiPath")
        val content = Files.readString(openApiPath, Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        return yaml.load(content) as Map<String, Any>
    }

    /**
     * 맵에서 하위 맵을 안전하게 추출한다.
     */
    private fun requireMap(source: Map<String, Any>, key: String): Map<String, Any> {
        val value = source[key]
        assertNotNull(value, "필수 섹션이 누락되었습니다: $key")
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any>
    }

    /**
     * 맵에서 배열 필드를 안전하게 추출한다.
     */
    private fun requireList(source: Map<String, Any>, key: String): List<Any> {
        val value = source[key]
        assertNotNull(value, "필수 리스트가 누락되었습니다: $key")
        @Suppress("UNCHECKED_CAST")
        return value as List<Any>
    }
}
