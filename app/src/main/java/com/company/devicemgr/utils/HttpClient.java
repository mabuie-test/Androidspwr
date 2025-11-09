package com.company.devicemgr.utils;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Protocol;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.util.Collections;

/**
* HttpClient utilitário para a app.
* - Timeouts aumentados
* - Força HTTP/1.1 (mitiga alguns problemas HTTP/2 / proxys)
* - Uploads por streaming (não carregamos o ficheiro todo em memória)
* - Retries com backoff para uploads
*/
public class HttpClient {
	private static final String TAG = "HttpClient";
	
	// Cliente OkHttp configurado com timeouts mais largos
	private static OkHttpClient client = new OkHttpClient.Builder()
	.connectTimeout(60, TimeUnit.SECONDS)
	.writeTimeout(180, TimeUnit.SECONDS)
	.readTimeout(120, TimeUnit.SECONDS)
	.retryOnConnectionFailure(true)
	.protocols(Collections.singletonList(Protocol.HTTP_1_1)) // força HTTP/1.1
	.build();
	
	// MediaType JSON
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	
	// POST JSON, devolve string de resposta
	public static String postJson(String url, String json, String bearerToken) throws IOException {
		RequestBody body = RequestBody.create(JSON, json);
		Request.Builder rb = new Request.Builder().url(url).post(body);
		if (bearerToken != null && bearerToken.length() > 0) rb.header("Authorization", "Bearer " + bearerToken);
		Request request = rb.build();
		try (Response res = client.newCall(request).execute()) {
			String s = res.body() != null ? res.body().string() : null;
			return s;
		}
	}
	
	// Upload de ficheiro (campo form `fieldName`) usando multipart/form-data
	// versão principal que recebe bytes (mantida para compatibilidade)
	public static String uploadFile(String url, String fieldName, String filename, byte[] data, String mimeType, String bearerToken) throws IOException {
		MediaType mt = MediaType.parse(mimeType != null ? mimeType : "application/octet-stream");
		RequestBody fileBody = RequestBody.create(mt, data);
		
		MultipartBody requestBody = new MultipartBody.Builder()
		.setType(MultipartBody.FORM)
		.addFormDataPart(fieldName, filename, fileBody)
		.build();
		
		Request.Builder rb = new Request.Builder().url(url).post(requestBody);
		if (bearerToken != null && bearerToken.length() > 0) rb.header("Authorization", "Bearer " + bearerToken);
		Request request = rb.build();
		
		// Retentar em caso de falha I/O
		IOException lastEx = null;
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try (Response res = client.newCall(request).execute()) {
				if (!res.isSuccessful()) {
					String body = res.body() != null ? res.body().string() : "<no body>";
					throw new IOException("upload failed code=" + res.code() + " body=" + body);
				}
				return res.body() != null ? res.body().string() : "";
				} catch (IOException e) {
				lastEx = e;
				Log.e(TAG, "upload (bytes) attempt " + attempt + " failed: " + e.getMessage());
				if (attempt < maxAttempts) {
					try { Thread.sleep(1000L * (long)Math.pow(2, attempt)); } catch (InterruptedException ignored) {}
				}
			}
		}
		throw lastEx != null ? lastEx : new IOException("upload_failed_unknown");
	}
	
	// Conveniência: aceita File diretamente (faz streaming do ficheiro, não lê tudo em memória)
	public static String uploadFile(String url, String fieldName, File file, String bearerToken) throws IOException {
		if (file == null || !file.exists()) throw new IOException("file not found");
		// tenta adivinhar mime type pelo nome
		String mime = URLConnection.guessContentTypeFromName(file.getName());
		if (mime == null) mime = "application/octet-stream";
		
		MediaType mt = MediaType.parse(mime);
		RequestBody fileBody = RequestBody.create(mt, file); // streaming
		
		MultipartBody requestBody = new MultipartBody.Builder()
		.setType(MultipartBody.FORM)
		.addFormDataPart(fieldName, file.getName(), fileBody)
		.build();
		
		Request.Builder rb = new Request.Builder().url(url).post(requestBody);
		if (bearerToken != null && bearerToken.length() > 0) rb.header("Authorization", "Bearer " + bearerToken);
		Request request = rb.build();
		
		// Retry com backoff
		IOException lastEx = null;
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			Log.i(TAG, "upload attempt " + attempt + " file=" + file.getAbsolutePath() + " size=" + file.length());
			try (Response res = client.newCall(request).execute()) {
				if (!res.isSuccessful()) {
					String body = res.body() != null ? res.body().string() : "<no body>";
					throw new IOException("upload failed code=" + res.code() + " body=" + body);
				}
				String s = res.body() != null ? res.body().string() : "";
				Log.i(TAG, "upload success: " + file.getName() + " respLen=" + (s != null ? s.length() : 0));
				return s;
				} catch (IOException e) {
				lastEx = e;
				Log.e(TAG, "upload attempt " + attempt + " failed: " + e.getMessage(), e);
				// se for a ultima tentativa, não esperar
				if (attempt < maxAttempts) {
					try { Thread.sleep(1000L * (long)Math.pow(2, attempt)); } catch (InterruptedException ignored) {}
				}
			}
		}
		throw lastEx != null ? lastEx : new IOException("upload_failed_unknown");
	}
}