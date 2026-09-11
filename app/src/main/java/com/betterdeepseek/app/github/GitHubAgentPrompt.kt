package com.betterdeepseek.app.github

object GitHubAgentPrompt {

    /**
     * Builds the system prompt / tool definition text injected into the AI model's context
     * so it is aware of its active GitHub access, repository, and editing/committing capabilities.
     */
    fun buildAgentSystemPrompt(
        username: String,
        targetRepo: String?,
        targetBranch: String = "main",
        canWrite: Boolean = true
    ): String {
        val repoSection = if (!targetRepo.isNullOrBlank()) {
            """
            - Target Repository: $targetRepo
            - Working Branch: $targetBranch
            """.trimIndent()
        } else {
            "- Target Repository: (Not yet selected. You can ask the user which repository to work on, or accept a repository name/URL from them)."
        }

        val writeCapabilities = if (canWrite) {
            """
            - Write & Edit Permissions: ACTIVE (Full write access to repositories)
            - You can propose and execute file edits, commit changes directly to the repository, create new branches, and open Pull Requests.
            """.trimIndent()
        } else {
            """
            - Write & Edit Permissions: READ-ONLY (You can view files and inspect repository structure).
            """.trimIndent()
        }

        return """
        # GitHub Agent Integration (Active)
        You have direct integration with the user's GitHub account via the Better DeepSeek Android host.
        
        ## Connection Info:
        - Authenticated GitHub User: @$username
        $repoSection
        $writeCapabilities
        
        ## Available Agent Tool Commands:
        When the user asks you to read, edit, commit code, or manage their repository, you are fully authorized and capable. You can output code and invoke the following action blocks, which the Better DeepSeek host will detect and execute:
        
        1. Commit File Edit:
        ```github-action:commit
        {
          "repo": "${targetRepo ?: "owner/repo"}",
          "branch": "$targetBranch",
          "path": "path/to/file.ext",
          "message": "Descriptive commit message",
          "content": "Full new file content"
        }
        ```
        
        2. Create Branch:
        ```github-action:create-branch
        {
          "repo": "${targetRepo ?: "owner/repo"}",
          "newBranch": "feature/branch-name",
          "fromBranch": "$targetBranch"
        }
        ```
        
        3. Create Pull Request:
        ```github-action:create-pr
        {
          "repo": "${targetRepo ?: "owner/repo"}",
          "title": "PR Title",
          "body": "PR Description",
          "head": "feature/branch-name",
          "base": "$targetBranch"
        }
        ```
        
        Always explain what changes you are making before committing. If you are updating an existing codebase, keep unchanged parts intact and provide clean, robust code.
        """.trimIndent()
    }
}
