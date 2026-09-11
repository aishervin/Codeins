package com.betterdeepseek.app.github

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String,
    val publicRepos: Int,
    val totalPrivateRepos: Int,
    val scopes: List<String>
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val description: String?,
    val htmlUrl: String
)

data class GitHubFileContent(
    val path: String,
    val sha: String,
    val content: String,
    val size: Long,
    val encoding: String
)

data class CommitResult(
    val commitSha: String,
    val fileSha: String,
    val commitUrl: String
)

class GitHubService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GitHubService"
        const val GITHUB_API_URL = "https://api.github.com"
        // Default Client ID for GitHub Device Authorization Flow
        // Users can also customize this in settings or use personal tokens
        const val DEFAULT_CLIENT_ID = "Iv1.b507a08c87ecfe98"
        const val SCOPES = "repo,read:user,workflow"
    }

    /**
     * Step 1 of GitHub Device Flow: request user and device code
     */
    fun startDeviceCodeFlow(
        clientId: String = DEFAULT_CLIENT_ID,
        callback: (Result<DeviceCodeResponse>) -> Unit
    ) {
        val url = "https://github.com/login/device/code"
        val json = JSONObject().apply {
            put("client_id", clientId)
            put("scope", SCOPES)
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    try {
                        val jsonResp = JSONObject(body)
                        if (jsonResp.has("error")) {
                            callback(Result.failure(Exception(jsonResp.optString("error_description", jsonResp.optString("error")))))
                            return
                        }
                        val result = DeviceCodeResponse(
                            deviceCode = jsonResp.getString("device_code"),
                            userCode = jsonResp.getString("user_code"),
                            verificationUri = jsonResp.optString("verification_uri", "https://github.com/login/device"),
                            expiresIn = jsonResp.optInt("expires_in", 900),
                            interval = jsonResp.optInt("interval", 5)
                        )
                        callback(Result.success(result))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }

    /**
     * Poll for access token in Device Flow
     */
    fun pollDeviceToken(
        clientId: String = DEFAULT_CLIENT_ID,
        deviceCode: String,
        callback: (Result<String>) -> Unit
    ) {
        val url = "https://github.com/login/oauth/access_token"
        val json = JSONObject().apply {
            put("client_id", clientId)
            put("device_code", deviceCode)
            put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    try {
                        val jsonResp = JSONObject(body)
                        if (jsonResp.has("access_token")) {
                            callback(Result.success(jsonResp.getString("access_token")))
                        } else {
                            val error = jsonResp.optString("error")
                            val desc = jsonResp.optString("error_description", error)
                            callback(Result.failure(Exception(desc)))
                        }
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }

    /**
     * Validate token and fetch user profile
     */
    fun fetchUserProfile(token: String, callback: (Result<GitHubUser>) -> Unit) {
        val request = Request.Builder()
            .url("$GITHUB_API_URL/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterDeepSeek-Android")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback(Result.failure(Exception("GitHub API error: ${resp.code} ${resp.message}")))
                        return
                    }
                    val scopeHeader = resp.header("X-OAuth-Scopes").orEmpty()
                    val scopes = scopeHeader.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val body = resp.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body)
                        val user = GitHubUser(
                            login = json.getString("login"),
                            name = if (json.has("name") && !json.isNull("name")) json.getString("name") else null,
                            avatarUrl = if (json.has("avatar_url") && !json.isNull("avatar_url")) json.getString("avatar_url") else null,
                            htmlUrl = json.optString("html_url", "https://github.com"),
                            publicRepos = json.optInt("public_repos", 0),
                            totalPrivateRepos = json.optInt("total_private_repos", 0),
                            scopes = scopes
                        )
                        callback(Result.success(user))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }

    /**
     * Fetch repositories accessible by the user
     */
    fun fetchUserRepos(token: String, callback: (Result<List<GitHubRepo>>) -> Unit) {
        val request = Request.Builder()
            .url("$GITHUB_API_URL/user/repos?sort=updated&per_page=100&type=all")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterDeepSeek-Android")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback(Result.failure(Exception("Failed to load repos: ${resp.code}")))
                        return
                    }
                    val body = resp.body?.string().orEmpty()
                    try {
                        val array = JSONArray(body)
                        val list = mutableListOf<GitHubRepo>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            list.add(
                                GitHubRepo(
                                    id = item.getLong("id"),
                                    name = item.getString("name"),
                                    fullName = item.getString("full_name"),
                                    owner = item.getJSONObject("owner").getString("login"),
                                    isPrivate = item.optBoolean("private", false),
                                    defaultBranch = item.optString("default_branch", "main"),
                                    description = if (item.has("description") && !item.isNull("description")) item.getString("description") else null,
                                    htmlUrl = item.optString("html_url", "")
                                )
                            )
                        }
                        callback(Result.success(list))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }

    /**
     * Read file content from repository
     */
    fun fetchFileContent(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String? = null,
        callback: (Result<GitHubFileContent>) -> Unit
    ) {
        var url = "$GITHUB_API_URL/repos/$owner/$repo/contents/$path"
        if (!branch.isNullOrBlank()) {
            url += "?ref=$branch"
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterDeepSeek-Android")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback(Result.failure(Exception("File not found or error ${resp.code}: $path")))
                        return
                    }
                    val body = resp.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body)
                        val rawContent = json.optString("content", "")
                        val encoding = json.optString("encoding", "base64")
                        val cleanBase64 = rawContent.replace("\n", "").replace("\r", "")
                        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        val text = String(decodedBytes, StandardCharsets.UTF_8)
                        val fileContent = GitHubFileContent(
                            path = json.optString("path", path),
                            sha = json.getString("sha"),
                            content = text,
                            size = json.optLong("size", 0L),
                            encoding = encoding
                        )
                        callback(Result.success(fileContent))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }

    /**
     * Write / update file with commit
     */
    fun commitFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String,
        existingSha: String? = null,
        callback: (Result<CommitResult>) -> Unit
    ) {
        fun doPut(shaToUse: String?) {
            val url = "$GITHUB_API_URL/repos/$owner/$repo/contents/$path"
            val base64Content = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                put("branch", branch)
                if (!shaToUse.isNullOrBlank()) {
                    put("sha", shaToUse)
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BetterDeepSeek-Android")
                .put(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    callback(Result.failure(e))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { resp ->
                        val respBody = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            callback(Result.failure(Exception("GitHub Commit failed (${resp.code}): $respBody")))
                            return
                        }
                        try {
                            val jsonResp = JSONObject(respBody)
                            val commitObj = jsonResp.getJSONObject("commit")
                            val contentObj = jsonResp.getJSONObject("content")
                            val result = CommitResult(
                                commitSha = commitObj.getString("sha"),
                                fileSha = contentObj.getString("sha"),
                                commitUrl = commitObj.optString("html_url", "")
                            )
                            callback(Result.success(result))
                        } catch (t: Throwable) {
                            callback(Result.failure(t))
                        }
                    }
                }
            })
        }

        if (existingSha != null) {
            doPut(existingSha)
        } else {
            // Check if file exists to fetch sha
            fetchFileContent(token, owner, repo, path, branch) { res ->
                val sha = res.getOrNull()?.sha
                doPut(sha)
            }
        }
    }

    /**
     * Create branch from existing branch
     */
    fun createBranch(
        token: String,
        owner: String,
        repo: String,
        newBranch: String,
        fromBranch: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        // Step 1: get ref of fromBranch
        val refUrl = "$GITHUB_API_URL/repos/$owner/$repo/git/ref/heads/$fromBranch"
        val request = Request.Builder()
            .url(refUrl)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterDeepSeek-Android")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback(Result.failure(Exception("Could not find base branch '$fromBranch' (${resp.code})")))
                        return
                    }
                    val body = resp.body?.string().orEmpty()
                    val sha = try {
                        JSONObject(body).getJSONObject("object").getString("sha")
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                        return
                    }

                    // Step 2: create new ref
                    val createUrl = "$GITHUB_API_URL/repos/$owner/$repo/git/refs"
                    val createJson = JSONObject().apply {
                        put("ref", "refs/heads/$newBranch")
                        put("sha", sha)
                    }
                    val createReq = Request.Builder()
                        .url(createUrl)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "BetterDeepSeek-Android")
                        .post(createJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(createReq).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            callback(Result.failure(e))
                        }

                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            response.use { cResp ->
                                if (cResp.isSuccessful) {
                                    callback(Result.success(true))
                                } else {
                                    val err = cResp.body?.string().orEmpty()
                                    callback(Result.failure(Exception("Failed to create branch: $err")))
                                }
                            }
                        }
                    })
                }
            }
        })
    }

    /**
     * Create a Pull Request
     */
    fun createPullRequest(
        token: String,
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
        callback: (Result<String>) -> Unit
    ) {
        val url = "$GITHUB_API_URL/repos/$owner/$repo/pulls"
        val json = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("head", head)
            put("base", base)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterDeepSeek-Android")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        callback(Result.failure(Exception("Failed to create PR: $respBody")))
                        return
                    }
                    try {
                        val prUrl = JSONObject(respBody).optString("html_url", "")
                        callback(Result.success(prUrl))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                }
            }
        })
    }
}
