/**
 * Better DeepSeek - GitHub Agent Bridge
 * Integrates GitHub coding agent capabilities into the DeepSeek chat interface.
 */
(function () {
    if (window.__bdsGitHubAgentInitialized) return;
    window.__bdsGitHubAgentInitialized = true;

    window.BDS_GITHUB = {
        getStatus: function () {
            try {
                if (window.AndroidBridge && window.AndroidBridge.getGitHubStatus) {
                    return JSON.parse(window.AndroidBridge.getGitHubStatus());
                }
            } catch (e) {
                console.error('[BDS-GitHub] getStatus error:', e);
            }
            return { connected: false };
        },

        openSettings: function () {
            if (window.AndroidBridge && window.AndroidBridge.openGitHubSettings) {
                window.AndroidBridge.openGitHubSettings();
            }
        },

        commitFile: function (opts) {
            if (!window.AndroidBridge) return Promise.reject(new Error('AndroidBridge not found'));
            var payload = {
                type: 'bds-github-commit-file',
                owner: opts.owner,
                repo: opts.repo,
                path: opts.path,
                content: opts.content,
                message: opts.message || ('Update ' + opts.path + ' via DeepSeek Agent'),
                branch: opts.branch || 'main',
                sha: opts.sha
            };
            return new Promise(function (resolve, reject) {
                try {
                    var res = JSON.parse(window.AndroidBridge.fetch(JSON.stringify(payload)));
                    if (res.ok) resolve(res);
                    else reject(new Error(res.error || 'Commit failed'));
                } catch (err) {
                    reject(err);
                }
            });
        },

        createBranch: function (opts) {
            if (!window.AndroidBridge) return Promise.reject(new Error('AndroidBridge not found'));
            var payload = {
                type: 'bds-github-create-branch',
                owner: opts.owner,
                repo: opts.repo,
                newBranch: opts.newBranch,
                fromBranch: opts.fromBranch || 'main'
            };
            return new Promise(function (resolve, reject) {
                try {
                    var res = JSON.parse(window.AndroidBridge.fetch(JSON.stringify(payload)));
                    if (res.ok) resolve(res);
                    else reject(new Error(res.error || 'Create branch failed'));
                } catch (err) {
                    reject(err);
                }
            });
        },

        createPR: function (opts) {
            if (!window.AndroidBridge) return Promise.reject(new Error('AndroidBridge not found'));
            var payload = {
                type: 'bds-github-create-pr',
                owner: opts.owner,
                repo: opts.repo,
                title: opts.title,
                body: opts.body || '',
                head: opts.head,
                base: opts.base || 'main'
            };
            return new Promise(function (resolve, reject) {
                try {
                    var res = JSON.parse(window.AndroidBridge.fetch(JSON.stringify(payload)));
                    if (res.ok) resolve(res);
                    else reject(new Error(res.error || 'Create PR failed'));
                } catch (err) {
                    reject(err);
                }
            });
        }
    };

    // Render floating status pill on DeepSeek UI
    function renderStatusPill() {
        var existing = document.getElementById('bds-github-pill');
        if (existing) existing.remove();

        var status = window.BDS_GITHUB.getStatus();
        var pill = document.createElement('div');
        pill.id = 'bds-github-pill';
        pill.style.position = 'fixed';
        pill.style.top = '12px';
        pill.style.right = '12px';
        pill.style.zIndex = '99999';
        pill.style.display = 'flex';
        pill.style.alignItems = 'center';
        pill.style.gap = '6px';
        pill.style.padding = '6px 12px';
        pill.style.borderRadius = '20px';
        pill.style.fontSize = '12px';
        pill.style.fontFamily = 'system-ui, -apple-system, sans-serif';
        pill.style.cursor = 'pointer';
        pill.style.boxShadow = '0 2px 8px rgba(0,0,0,0.15)';
        pill.style.transition = 'all 0.2s ease';

        if (status.connected) {
            pill.style.backgroundColor = '#16a34a';
            pill.style.color = '#ffffff';
            var repoText = status.targetRepo ? ' (' + status.targetRepo + ')' : '';
            pill.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12,2A10,10 0 0,0 2,12C2,16.42 4.87,20.17 8.84,21.5C9.34,21.58 9.5,21.27 9.5,21C9.5,20.77 9.5,20.14 9.5,19.31C6.73,19.91 6.14,17.97 6.14,17.97C5.68,16.81 5.03,16.5 5.03,16.5C4.12,15.88 5.1,15.9 5.1,15.9C6.1,15.97 6.63,16.93 6.63,16.93C7.5,18.45 8.97,18 9.54,17.76C9.63,17.11 9.89,16.67 10.17,16.42C7.95,16.17 5.62,15.31 5.62,11.5C5.62,10.39 6,9.5 6.65,8.79C6.55,8.54 6.2,7.5 6.75,6.15C6.75,6.15 7.59,5.88 9.5,7.17C10.29,6.95 11.15,6.84 12,6.84C12.85,6.84 13.71,6.95 14.5,7.17C16.41,5.88 17.25,6.15 17.25,6.15C17.8,7.5 17.45,8.54 17.35,8.79C18,9.5 18.38,10.39 18.38,11.5C18.38,15.32 16.04,16.16 13.81,16.41C14.17,16.72 14.5,17.33 14.5,18.26C14.5,19.6 14.5,20.68 14.5,21C14.5,21.27 14.66,21.59 15.17,21.5C19.14,20.16 22,16.42 22,12A10,10 0 0,0 12,2Z"/></svg>'
                + '<span>GitHub: @' + status.username + repoText + '</span>';
        } else {
            pill.style.backgroundColor = '#24292f';
            pill.style.color = '#ffffff';
            pill.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12,2A10,10 0 0,0 2,12C2,16.42 4.87,20.17 8.84,21.5C9.34,21.58 9.5,21.27 9.5,21C9.5,20.77 9.5,20.14 9.5,19.31C6.73,19.91 6.14,17.97 6.14,17.97C5.68,16.81 5.03,16.5 5.03,16.5C4.12,15.88 5.1,15.9 5.1,15.9C6.1,15.97 6.63,16.93 6.63,16.93C7.5,18.45 8.97,18 9.54,17.76C9.63,17.11 9.89,16.67 10.17,16.42C7.95,16.17 5.62,15.31 5.62,11.5C5.62,10.39 6,9.5 6.65,8.79C6.55,8.54 6.2,7.5 6.75,6.15C6.75,6.15 7.59,5.88 9.5,7.17C10.29,6.95 11.15,6.84 12,6.84C12.85,6.84 13.71,6.95 14.5,7.17C16.41,5.88 17.25,6.15 17.25,6.15C17.8,7.5 17.45,8.54 17.35,8.79C18,9.5 18.38,10.39 18.38,11.5C18.38,15.32 16.04,16.16 13.81,16.41C14.17,16.72 14.5,17.33 14.5,18.26C14.5,19.6 14.5,20.68 14.5,21C14.5,21.27 14.66,21.59 15.17,21.5C19.14,20.16 22,16.42 22,12A10,10 0 0,0 12,2Z"/></svg>'
                + '<span>اتصال به گیت‌هاب</span>';
        }

        pill.onclick = function () {
            window.BDS_GITHUB.openSettings();
        };

        document.body.appendChild(pill);
    }

    // Parse model responses and attach 1-click execution buttons for GitHub actions
    function scanAndEnhanceActionBlocks() {
        var codeBlocks = document.querySelectorAll('pre code');
        codeBlocks.forEach(function (block) {
            if (block.dataset.bdsGithubProcessed) return;
            var text = block.textContent.trim();

            if (text.startsWith('{') && (text.includes('"repo"') || text.includes('"path"')) && text.includes('"content"')) {
                try {
                    var actionData = JSON.parse(text);
                    if (actionData.path && actionData.content) {
                        block.dataset.bdsGithubProcessed = 'true';
                        attachCommitButton(block, actionData);
                    }
                } catch (e) {
                    // Not a pure JSON block, ignore
                }
            }
        });
    }

    function attachCommitButton(codeElement, actionData) {
        var parentPre = codeElement.closest('pre') || codeElement;
        var btn = document.createElement('button');
        btn.innerText = '⚡ کامیت روی گیت‌هاب (' + actionData.path + ')';
        btn.style.marginTop = '8px';
        btn.style.padding = '8px 14px';
        btn.style.borderRadius = '6px';
        btn.style.backgroundColor = '#0969da';
        btn.style.color = '#ffffff';
        btn.style.fontWeight = 'bold';
        btn.style.border = 'none';
        btn.style.cursor = 'pointer';
        btn.style.display = 'block';

        btn.onclick = function () {
            btn.disabled = true;
            btn.innerText = 'در حال کامیت روی گیت‌هاب...';
            var status = window.BDS_GITHUB.getStatus();
            var targetRepo = actionData.repo || status.targetRepo;
            if (!targetRepo || !targetRepo.includes('/')) {
                alert('لطفاً در تنظیمات گیت‌هاب، مخزن هدف (owner/repo) را مشخص کنید.');
                btn.disabled = false;
                btn.innerText = '⚡ کامیت روی گیت‌هاب (' + actionData.path + ')';
                window.BDS_GITHUB.openSettings();
                return;
            }
            var parts = targetRepo.split('/');
            window.BDS_GITHUB.commitFile({
                owner: parts[0].trim(),
                repo: parts[1].trim(),
                path: actionData.path,
                content: actionData.content,
                message: actionData.message || ('Update ' + actionData.path),
                branch: actionData.branch || status.targetBranch || 'main'
            }).then(function (res) {
                btn.style.backgroundColor = '#16a34a';
                btn.innerText = '✓ با موفقیت کامیت شد!';
            }).catch(function (err) {
                btn.style.backgroundColor = '#dc2626';
                btn.innerText = 'خطا در کامیت: ' + err.message;
                setTimeout(function () {
                    btn.disabled = false;
                    btn.style.backgroundColor = '#0969da';
                    btn.innerText = '⚡ کامیت مجدد (' + actionData.path + ')';
                }, 3000);
            });
        };

        parentPre.parentNode.insertBefore(btn, parentPre.nextSibling);
    }

    // Periodic check & mutation observer
    setInterval(scanAndEnhanceActionBlocks, 2000);
    setTimeout(renderStatusPill, 1500);

    // Refresh pill when window focused or storage changed
    window.addEventListener('focus', renderStatusPill);
    window.addEventListener('bds-github-updated', renderStatusPill);
})();
