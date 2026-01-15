package com.inkflow.media.infra.image

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.media.application.MediaImageProcessor
import com.inkflow.media.application.MediaJobSpec
import com.inkflow.media.application.MediaThumbnailProperties
import com.inkflow.media.application.MediaThumbnailResult
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ImageIO를 사용해 썸네일을 생성하는 구현체.
 */
@Component
class ImageIOThumbnailProcessor(
    private val properties: MediaThumbnailProperties
) : MediaImageProcessor {
    init {
        // ImageIO가 임시 파일 캐시를 사용하지 않도록 설정한다.
        ImageIO.setUseCache(false)
    }

    /**
     * 원본 이미지를 썸네일 규격에 맞춰 변환한다.
     */
    override fun createThumbnail(originalBytes: ByteArray, spec: MediaJobSpec): MediaThumbnailResult {
        if (originalBytes.isEmpty()) {
            throw invalid("image", "원본 이미지가 비어 있습니다.")
        }
        val outputFormat = normalizeFormat(spec.format)
        val sourceImage = readImage(originalBytes)
        val resizedImage = resizeToCanvas(sourceImage, spec.width, spec.height, outputFormat)
        val encoded = encodeImage(resizedImage, outputFormat)
        val contentType = resolveContentType(outputFormat)

        return MediaThumbnailResult(
            bytes = encoded,
            contentType = contentType,
            format = outputFormat,
            width = spec.width,
            height = spec.height
        )
    }

    /**
     * 요청 포맷을 표준 포맷으로 정규화한다.
     */
    private fun normalizeFormat(format: String): String {
        val normalized = format.trim().lowercase()
        val canonical = if (normalized == "jpg") "jpeg" else normalized
        val allowed = properties.allowedFormats
            .map { if (it.lowercase() == "jpg") "jpeg" else it.lowercase() }
            .toSet()
        if (allowed.isNotEmpty() && canonical !in allowed) {
            throw invalid("format", "지원하지 않는 이미지 포맷입니다.")
        }
        return canonical
    }

    /**
     * 원본 바이트 배열을 BufferedImage로 변환한다.
     */
    private fun readImage(originalBytes: ByteArray): BufferedImage {
        return ImageIO.read(ByteArrayInputStream(originalBytes))
            ?: throw invalid("image", "이미지 디코딩에 실패했습니다.")
    }

    /**
     * 지정한 크기 내에서 비율을 유지하며 캔버스에 리사이즈한다.
     */
    private fun resizeToCanvas(source: BufferedImage, targetWidth: Int, targetHeight: Int, format: String): BufferedImage {
        val ratio = min(targetWidth.toDouble() / source.width, targetHeight.toDouble() / source.height)
        val scaledWidth = max(1, (source.width * ratio).roundToInt())
        val scaledHeight = max(1, (source.height * ratio).roundToInt())
        val imageType = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val canvas = BufferedImage(targetWidth, targetHeight, imageType)
        val graphics = canvas.createGraphics()

        // 렌더링 품질을 우선하는 힌트를 적용한다.
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // JPEG는 투명도를 지원하지 않으므로 배경색을 채운다.
        val background = resolveBackgroundColor(format)
        graphics.color = background
        graphics.fillRect(0, 0, targetWidth, targetHeight)

        val offsetX = (targetWidth - scaledWidth) / 2
        val offsetY = (targetHeight - scaledHeight) / 2
        graphics.drawImage(source, offsetX, offsetY, scaledWidth, scaledHeight, null)
        graphics.dispose()

        return canvas
    }

    /**
     * 포맷에 맞는 이미지 인코딩을 수행한다.
     */
    private fun encodeImage(image: BufferedImage, format: String): ByteArray {
        return if (format == "jpeg") {
            encodeJpeg(image)
        } else {
            encodePng(image)
        }
    }

    /**
     * JPEG 인코딩 시 압축 품질을 반영한다.
     */
    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: throw invalid("format", "JPEG 인코더를 찾을 수 없습니다.")
        val imageOutput = ImageIO.createImageOutputStream(outputStream)
        writer.output = imageOutput

        val params = writer.defaultWriteParam
        if (params.canWriteCompressed()) {
            params.compressionMode = ImageWriteParam.MODE_EXPLICIT
            params.compressionQuality = properties.jpegQuality.toFloat()
        }

        writer.write(null, IIOImage(image, null, null), params)
        imageOutput.close()
        writer.dispose()

        return outputStream.toByteArray()
    }

    /**
     * PNG 인코딩을 수행한다.
     */
    private fun encodePng(image: BufferedImage): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        return outputStream.toByteArray()
    }

    /**
     * 포맷에 맞는 Content-Type을 반환한다.
     */
    private fun resolveContentType(format: String): String {
        return when (format) {
            "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> throw invalid("format", "지원하지 않는 이미지 포맷입니다.")
        }
    }

    /**
     * 배경색 문자열을 Color로 변환한다.
     */
    private fun resolveBackgroundColor(format: String): Color {
        if (format == "png") {
            // PNG는 투명 배경을 지원하므로 알파 값을 고려한다.
            val parsed = parseColor(properties.backgroundColor)
            return Color(parsed.red, parsed.green, parsed.blue, parsed.alpha)
        }
        return parseColor(properties.backgroundColor)
    }

    /**
     * #RRGGBB 또는 #RRGGBBAA 형식의 색상 값을 파싱한다.
     */
    private fun parseColor(hex: String): Color {
        val sanitized = hex.trim().removePrefix("#")
        if (sanitized.length != 6 && sanitized.length != 8) {
            throw invalid("backgroundColor", "색상 값은 #RRGGBB 또는 #RRGGBBAA 형식이어야 합니다.")
        }
        val red = sanitized.substring(0, 2).toInt(16)
        val green = sanitized.substring(2, 4).toInt(16)
        val blue = sanitized.substring(4, 6).toInt(16)
        val alpha = if (sanitized.length == 8) sanitized.substring(6, 8).toInt(16) else 255
        return Color(red, green, blue, alpha)
    }

    /**
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }
}
