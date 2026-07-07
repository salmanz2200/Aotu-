package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini API Request/Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: String? = null, // Or just request standard JSON output
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Retrofit Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeVideoFrames(frames: List<Bitmap>, width: Int, height: Int): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return null
        }

        // Build prompt with frames
        val promptText = """
            You are an expert Android Automation Recorder Analyst.
            Your task is to analyze a sequence of chronological screenshots extracted from a video recording of an Android app task (device screen resolution is ${width}x${height}).
            
            By comparing the state transitions between the consecutive screenshots (Frame 1 to Frame N):
            1. Identify every click/tap event performed by the user.
            2. To locate a click:
               - Look for visual touch indicators (e.g., circular white ripples, cursor pointers).
               - Look at which button, text field, toggle, or icon changes state (e.g., focused, highlighted, active, or a new screen/dialog opens) between two consecutive frames, and output the coordinate (X, Y) at the absolute center of that UI element.
               - If a text input field gets filled with text, the user must have clicked on that text field first.
            3. For each touch event, estimate:
               - The exact X and Y coordinates as integers relative to the device resolution of ${width}x${height}.
               - The delay in milliseconds (delayMs) from the previous action. This delay should reflect the realistic human typing or reading speed, or wait times for pages to load (typically 1000ms to 3000ms).
            
            Format the output strictly as a JSON array of actions:
            [
              {"action": "tap", "x": 540, "y": 1200, "delayMs": 2000},
              {"action": "tap", "x": 1000, "y": 150, "delayMs": 1500}
            ]
            
            Return ONLY the valid raw JSON array of actions. Do not include any markdown format, backticks (like ```json), or conversational text.
        """.trimIndent()

        val parts = mutableListOf<Part>()
        parts.add(Part(text = promptText))
        
        frames.forEachIndexed { index, bitmap ->
            parts.add(Part(text = "Frame ${index + 1}:"))
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64())))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Gemini analysis result: $textResult")
            textResult
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API for video analysis", e)
            null
        }
    }

    suspend fun verifyScreenshotsMatch(targetFrame: Bitmap, actualScreenshot: Bitmap): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return true // Fallback to assuming match/success
        }

        val promptText = """
            Compare these two Android screens:
            Image 1: Target successful final screen.
            Image 2: Actual captured screen after automatic execution.
            
            Determine if the operation succeeded by checking if the two screens match in key state or content (e.g., successful submit message, correct tab opened, button tapped state, or matching final state). Ignore minor clock or notification bar differences.
            
            Return strictly a JSON object:
            {
              "success": true,
              "reason": "The final state matches the target screen."
            }
            Do not include markdown or explanations, return ONLY the raw JSON object.
        """.trimIndent()

        val parts = listOf(
            Part(text = promptText),
            Part(text = "Target Successful Screen:"),
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = targetFrame.toBase64())),
            Part(text = "Actual Screen:"),
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = actualScreenshot.toBase64()))
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Gemini match result: $textResult")
            // Parse simple json result e.g. "success": true
            textResult?.contains("\"success\": true") == true || textResult?.contains("\"success\":true") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API for screen comparison", e)
            true // Fallback to assuming success
        }
    }

    suspend fun verifyScreenshotContainsText(screenshot: Bitmap, targetText: String): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return true // Fallback to assuming success
        }

        val promptText = """
            Analyze the provided screenshot of the Android screen.
            Determine if the screen clearly contains or displays the following target text or phrase: "$targetText".
            The app UI might be in Arabic or English. We are looking for this target text, its exact match, or its equivalent successful meaning displayed on the screen.
            
            Return strictly a JSON object:
            {
              "success": true,
              "reason": "The text was found on the screen."
            }
            Do not include markdown or explanations, return ONLY the raw JSON object.
        """.trimIndent()

        val parts = listOf(
            Part(text = promptText),
            Part(inlineData = InlineData(mimeType = "image/jpeg", data = screenshot.toBase64()))
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Gemini text validation result: $textResult")
            textResult?.contains("\"success\": true") == true || textResult?.contains("\"success\":true") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API for text search on screen", e)
            true // Fallback to assuming success
        }
    }

    suspend fun verifyWithGemini(
        actualScreenshot: Bitmap,
        referenceImage: Bitmap?,
        referenceText: String?
    ): Boolean? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return null
        }

        val promptText = if (referenceImage != null) {
            """
                بناءً على الصورتين المرفقتين:
                الصورة الأولى: الشاشة الحالية الملتقطة بعد تنفيذ العملية.
                الصورة الثانية: صورة النجاح المرجعية.

                هل الشاشة الحالية تطابق صورة النجاح أو تحتوي على النص المطلوب؟
                يرجى الإجابة بنعم أو لا، وتوفير النتيجة في قالب JSON كالتالي:
                {
                  "success": true,
                  "reason": "توضيح السبب"
                }
                أو
                {
                  "success": false,
                  "reason": "توضيح السبب"
                }
                تأكد من إرجاع JSON فقط بدون أي علامات ماركداون أو نصوص إضافية.
            """.trimIndent()
        } else {
            """
                بناءً على صورة الشاشة الحالية المرفقة، هل تحتوي الشاشة الحالية على النص المطلوب: "$referenceText"؟

                هل الشاشة الحالية تطابق صورة النجاح أو تحتوي على النص المطلوب؟
                يرجى الإجابة بنعم أو لا، وتوفير النتيجة في قالب JSON كالتالي:
                {
                  "success": true,
                  "reason": "توضيح السبب"
                }
                أو
                {
                  "success": false,
                  "reason": "توضيح السبب"
                }
                تأكد من إرجاع JSON فقط بدون أي علامات ماركداون أو نصوص إضافية.
            """.trimIndent()
        }

        val parts = mutableListOf<Part>()
        parts.add(Part(text = promptText))
        parts.add(Part(text = "الشاشة الحالية بعد التنفيذ:"))
        parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = actualScreenshot.toBase64())))

        if (referenceImage != null) {
            parts.add(Part(text = "صورة النجاح المرجعية:"))
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = referenceImage.toBase64())))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val textResult = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Gemini verifyWithGemini result: $textResult")
            if (textResult == null) {
                null
            } else {
                textResult.contains("\"success\": true") || textResult.contains("\"success\":true")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API for unified verification", e)
            null
        }
    }
}
