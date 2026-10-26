# OpenChat

A native Android chat app with a ChatGPT-style interface that works with **any provider that speaks the OpenAI-compatible API**. You add your own Base URL and API key, fetch the provider's models, test them, enable the ones you want, and chat with them.

Built with Kotlin and Jetpack Compose. No account with OpenAI is required and the app has no backend server of its own.

> OpenChat is an independent project. It is **not affiliated with OpenAI**. ChatGPT is a trademark of OpenAI. Model names and responses belong to their respective providers.

## What is included

- ChatGPT-style light and dark interface with a conversation drawer
- Streaming replies, stop generation, regenerate and edit messages
- Markdown rendering with selectable text and copyable code blocks
- Reasoning/thinking text support when a provider sends `reasoning_content` or `reasoning`
- Image attachments for vision models (up to 4 images per message)
- Speech-to-text input using the phone's speech recognizer
- Multiple providers, each with its own Base URL and API key
- Fetch models from `GET /models`, test models with a tiny chat request, enable or disable models, and add model IDs manually
- Per-chat model selection, custom instructions, temperature, max token limit, streaming toggle, light/dark/system theme
- Pin, rename, search and delete conversations; export chats as JSON
- Local state encrypted with the Android Keystore; API keys are never included in exports
- Debug APK built automatically by GitHub Actions

## Requirements

- An Android 8.0 (API 26) or newer phone
- A free GitHub account (for the automatic APK build)
- A Base URL and API key from an OpenAI-compatible provider
- For local providers such as Ollama or LM Studio: the phone and computer on the same Wi-Fi network

## Build the APK with GitHub Actions (beginner steps)

You do **not** need to install Android Studio, Java, Gradle or any package. GitHub builds the APK for you.

1. Sign in to [github.com](https://github.com) and create a new **empty private or public repository**, for example `openchat`.
2. On the repository page, choose **Add file → Upload files**.
3. Open the `chatgptt` folder on your computer, select **everything inside it** (the `app` folder, `.github` folder and the root files such as `build.gradle.kts`), then drag it into the browser upload area. Uploading the outer `chatgptt` folder itself creates an extra nesting level and the build will fail.
4. Scroll down and press **Commit changes**.
5. Open the **Actions** tab of the repository. The workflow named **Build OpenChat APK** starts automatically for a push to `main` or `master`.
   - If no workflow appears, confirm the folder path is exactly `.github/workflows/build-apk.yml` (the folder name starts with a dot).
   - If Actions is disabled, open **Settings → Actions → General** and allow actions.
   - You can also run it manually: **Actions → Build OpenChat APK → Run workflow**.
6. Wait until the run shows a green check mark (usually 3–8 minutes on the first build).
7. Open the finished run, scroll to the **Artifacts** section at the bottom, and download **OpenChat-debug-apk**. It downloads as a `.zip` file.
8. Extract `app-debug.apk` from the zip and move it to your phone (USB cable, Google Drive, or another file transfer method).
9. On the phone, tap the APK. If Android asks, allow **Install unknown apps** for the app you used to open it (Files or Chrome), then confirm the installation.

The workflow keeps the APK artifact for 30 days. Pushing a new commit builds a fresh APK.

### If the build fails

- Make sure every project file was uploaded, especially `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` and `.github/workflows/build-apk.yml`.
- Open the red run, click the failed step, and read the last error lines. Common causes are a wrong folder structure or a deleted source file.
- Re-running the workflow: open the run and choose **Re-run all jobs**.

## Add a provider and its models in the app

1. Open the menu (☰) in the top-left corner, then **Settings**.
2. Tap **Providers and API keys → +**.
3. Enter:
   - **Provider name**: anything you like, for example `OpenAI`.
   - **Base URL**: the API root, usually ending in `/v1`. See the examples below.
   - **API key**: paste the key from your provider. It can be left empty for some local servers.
4. Tap **Add**. The model screen opens and tries to fetch the provider's model list automatically. If it does not, tap **Fetch models**.
5. Use the play button to test a model. A successful test also enables the model. You can also flip the switch yourself.
6. If the provider does not support `/models`, tap **Add manually**, enter the exact model ID, and test it.
7. Go back to the chat and tap the model name in the top bar to choose an enabled model.

### Common Base URLs

| Provider | Base URL |
| --- | --- |
| OpenAI | `https://api.openai.com/v1` |
| OpenRouter | `https://openrouter.ai/api/v1` |
| Groq | `https://api.groq.com/openai/v1` |
| Together AI | `https://api.together.xyz/v1` |
| Ollama on your computer | `http://<computer-ip>:11434/v1` |
| LM Studio on your computer | `http://<computer-ip>:1234/v1` |

The app calls `<Base URL>/models` and `<Base URL>/chat/completions` with a `Authorization: Bearer <API key>` header. Providers that use a different request format or a different authentication header are not supported.

### Local providers

- Find your computer's local IP address (for example `192.168.1.20`) and put it in the Base URL.
- Keep the phone and computer on the same network.
- Ollama must listen on all interfaces. Run it with `OLLAMA_HOST=0.0.0.0 ollama serve` and make sure your firewall allows port `11434`.
- LM Studio: enable the local server and "Serve on Local Network" in its server settings.
- Local HTTP is allowed by the app. Do not use untrusted Wi-Fi networks when sending private data.

## Privacy

- API keys, chats, models and settings are stored only on the phone, encrypted with a key held by the Android Keystore.
- Android cloud backup is disabled, so the encrypted state is not uploaded to Google Drive by this app.
- Requests go directly from the phone to the provider you choose. OpenChat has no server, no analytics and no tracking.
- Chat export contains conversations only. It does **not** contain API keys or provider configuration.

## Known limitations

This is a full native chat client, but it is not a literal copy of every server-side feature of ChatGPT. It uses the OpenAI-compatible **Chat Completions** API.

Not included:

- OpenAI account login or subscription management
- Image generation, browsing, code interpreter, connectors, canvas or memory features
- Voice conversation mode (speech input is supported; spoken replies are not generated)
- The OpenAI **Responses API**, Assistants API, or provider-native APIs that are not OpenAI-compatible
- Azure OpenAI's custom `api-key` header and deployment-style URLs
- Model-specific tools, function calling and file uploads other than images

These features depend on proprietary provider services or custom protocols. The app focuses on reliable text chat, vision input, streaming and flexible bring-your-own-key model management.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "App not installed" when updating OpenChat | Android only allows an update when the signing key matches. The workflow caches its debug key, but if the cache was cleared, uninstall the old OpenChat first. Uninstalling deletes local chats and saved API keys. |
| `401` or `Unauthorized` | The API key is wrong, expired, or the provider requires billing/credits. |
| `404` or "Not found" | The Base URL is usually wrong. It must point to the API root that contains `/chat/completions` and often ends with `/v1`. |
| Model list is empty | The provider may not support `GET /models`. Add the exact model ID manually. |
| Model test fails but you can browse the provider | Free tiers may not allow the test request, or the provider rejects tiny requests. Try another model or enable it manually and send a real message. |
| Connection refused with Ollama/LM Studio | The server is not exposed on the LAN, the IP/port is wrong, or a firewall blocks it. |
| "Stop generating" leaves partial text | That is intended: the partial reply is saved and marked as stopped. |
| Provider removed by accident | Add it again with the same Base URL and key. Existing conversations stay on the phone. |

## Project layout

```
app/src/main/java/com/openchat/app/
  MainActivity.kt          App entry point
  AppRoot.kt               Navigation, drawer, snackbar and loading state
  ChatViewModel.kt         All app actions and streaming orchestration
  AppUiState.kt            UI state and model selection logic
  data/                    Models, encrypted storage and Keystore crypto
  network/ApiClient.kt     OpenAI-compatible HTTP and SSE client
  ui/                      Chat, drawer, provider, model and settings screens
  ui/theme/                ChatGPT-style colors and typography
  util/ImageUtils.kt       Image resize, rotation and JPEG conversion
.github/workflows/         Automatic APK build
```

The build uses AGP 8.7.3, Kotlin 2.0.21, JDK 17 and Gradle 8.9. The GitHub workflow provisions Gradle itself, so the repository does not need a Gradle wrapper or local Android SDK.
