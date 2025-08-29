package com.claudecodechat.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.claudecodechat.models.ImageAttachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Utility for handling clipboard images
 */
object ClipboardImageHandler {
    private val logger = Logger.getInstance(ClipboardImageHandler::class.java)
    
    private const val MAX_IMAGE_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_DIMENSION = 1568 // Max dimension before resizing
    private const val THUMBNAIL_SIZE = 100
    
    /**
     * Extract image from clipboard if available
     */
    fun extractImageFromClipboard(): ImageAttachment? {
        try {
            val clipboard = CopyPasteManager.getInstance()
            val contents = clipboard.contents ?: return null
            
            // Check if clipboard contains an image
            if (!contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return null
            }
            
            // Get the image from clipboard
            val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image ?: return null
            
            // Convert to BufferedImage
            val bufferedImage = convertToBufferedImage(image)
            
            // Resize if necessary
            val resizedImage = resizeIfNeeded(bufferedImage)
            
            // Convert to base64
            val (base64Data, mediaType, sizeBytes) = encodeToBase64(resizedImage)
            
            // Check size limit
            if (sizeBytes > MAX_IMAGE_SIZE) {
                logger.warn("Image size exceeds limit: $sizeBytes bytes")
                return null
            }
            
            // Create thumbnail
            val thumbnail = createThumbnail(resizedImage)
            
            return ImageAttachment(
                displayId = 1, // Will be updated by the caller
                base64Data = base64Data,
                mediaType = mediaType,
                thumbnailBitmap = thumbnail,
                sizeBytes = sizeBytes
            )
            
        } catch (e: Exception) {
            logger.error("Failed to extract image from clipboard", e)
            return null
        }
    }
    
    /**
     * Check if clipboard contains an image
     */
    fun hasImageInClipboard(): Boolean {
        return try {
            val clipboard = CopyPasteManager.getInstance()
            val contents = clipboard.contents
            contents?.isDataFlavorSupported(DataFlavor.imageFlavor) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Convert Image to BufferedImage
     */
    private fun convertToBufferedImage(image: Image): BufferedImage {
        if (image is BufferedImage) {
            return image
        }
        
        val width = image.getWidth(null)
        val height = image.getHeight(null)
        
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        
        return bufferedImage
    }
    
    /**
     * Resize image if it exceeds maximum dimensions
     */
    private fun resizeIfNeeded(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val maxDim = maxOf(width, height)
        
        if (maxDim <= MAX_DIMENSION) {
            return image
        }
        
        val scale = MAX_DIMENSION.toDouble() / maxDim
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = resized.createGraphics()
        graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()
        
        return resized
    }
    
    /**
     * Encode image to base64
     */
    private fun encodeToBase64(image: BufferedImage): Triple<String, String, Long> {
        // Try PNG first for best quality
        var outputStream = ByteArrayOutputStream()
        var format = "png"
        var mediaType = "image/png"
        
        if (!ImageIO.write(image, format, outputStream)) {
            // Fall back to JPEG if PNG fails
            outputStream = ByteArrayOutputStream()
            format = "jpg"
            mediaType = "image/jpeg"
            
            // Convert to RGB for JPEG (no alpha channel)
            val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val graphics = rgbImage.createGraphics()
            graphics.drawImage(image, 0, 0, null)
            graphics.dispose()
            
            ImageIO.write(rgbImage, format, outputStream)
        }
        
        val imageBytes = outputStream.toByteArray()
        val base64 = Base64.getEncoder().encodeToString(imageBytes)
        
        return Triple(base64, mediaType, imageBytes.size.toLong())
    }
    
    /**
     * Create a thumbnail for UI display
     */
    private fun createThumbnail(image: BufferedImage): ImageBitmap? {
        return try {
            val aspectRatio = image.width.toDouble() / image.height
            val thumbWidth: Int
            val thumbHeight: Int
            
            if (aspectRatio > 1) {
                thumbWidth = THUMBNAIL_SIZE
                thumbHeight = (THUMBNAIL_SIZE / aspectRatio).toInt()
            } else {
                thumbHeight = THUMBNAIL_SIZE
                thumbWidth = (THUMBNAIL_SIZE * aspectRatio).toInt()
            }
            
            val thumbnail = BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_ARGB)
            val graphics = thumbnail.createGraphics()
            graphics.drawImage(
                image.getScaledInstance(thumbWidth, thumbHeight, Image.SCALE_SMOOTH),
                0, 0, null
            )
            graphics.dispose()
            
            // Convert to Compose ImageBitmap
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(thumbnail, "png", outputStream)
            val bytes = outputStream.toByteArray()
            
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            logger.error("Failed to create thumbnail", e)
            null
        }
    }
    
    /**
     * Detect media type from image data
     */
    fun detectMediaType(imageBytes: ByteArray): String {
        return when {
            imageBytes.size >= 8 && 
            imageBytes[0] == 0x89.toByte() && 
            imageBytes[1] == 0x50.toByte() && 
            imageBytes[2] == 0x4E.toByte() && 
            imageBytes[3] == 0x47.toByte() -> "image/png"
            
            imageBytes.size >= 3 && 
            imageBytes[0] == 0xFF.toByte() && 
            imageBytes[1] == 0xD8.toByte() && 
            imageBytes[2] == 0xFF.toByte() -> "image/jpeg"
            
            imageBytes.size >= 6 && 
            String(imageBytes.slice(0..5).toByteArray()) == "GIF87a" ||
            String(imageBytes.slice(0..5).toByteArray()) == "GIF89a" -> "image/gif"
            
            imageBytes.size >= 12 && 
            String(imageBytes.slice(8..11).toByteArray()) == "WEBP" -> "image/webp"
            
            else -> "image/png" // Default fallback
        }
    }
}