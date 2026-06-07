(() => {
    const MAX_MODEL_HISTORY_MESSAGES = 10;
    const MAX_STORED_CONVERSATIONS = 12;
    const MAX_STORED_MESSAGES = 40;
    const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    const MAX_IMAGE_DATA_URL_LENGTH = 190000;
    const CHAT_STORAGE_KEY = "bloodInventory.chatbot.conversations.v1";
    const ACTIVE_CHAT_KEY = "bloodInventory.chatbot.activeConversationId.v1";
    const widget = document.querySelector("[data-chatbot-widget]");

    if (!(widget instanceof HTMLElement)) {
        return;
    }

    const toggle = widget.querySelector("[data-chatbot-toggle]");
    const closeButton = widget.querySelector("[data-chatbot-close]");
    const newChatButton = widget.querySelector("[data-chatbot-new-chat]");
    const historyToggleButton = widget.querySelector("[data-chatbot-history-toggle]");
    const historyPanel = widget.querySelector("[data-chatbot-history-panel]");
    const historyList = widget.querySelector("[data-chatbot-history-list]");
    const historyClearButton = widget.querySelector("[data-chatbot-history-clear]");
    const panel = widget.querySelector("[data-chatbot-panel]");
    const form = widget.querySelector("[data-chatbot-form]");
    const input = widget.querySelector("[data-chatbot-input]");
    const sendButton = widget.querySelector("[data-chatbot-send]");
    const attachButton = widget.querySelector("[data-chatbot-attach]");
    const imageInput = widget.querySelector("[data-chatbot-image-input]");
    const imagePreview = widget.querySelector("[data-chatbot-image-preview]");
    const imageThumbnail = widget.querySelector("[data-chatbot-image-thumbnail]");
    const imageName = widget.querySelector("[data-chatbot-image-name]");
    const imageRemoveButton = widget.querySelector("[data-chatbot-image-remove]");
    const messages = widget.querySelector("[data-chatbot-messages]");
    const status = widget.querySelector("[data-chatbot-status]");
    const translate = window.BloodInventoryTranslate || ((value) => value);
    let selectedImage = null;
    let conversations = loadConversations();
    let activeConversationId = storageGet(ACTIVE_CHAT_KEY);

    if (!(toggle instanceof HTMLButtonElement)
            || !(closeButton instanceof HTMLButtonElement)
            || !(newChatButton instanceof HTMLButtonElement)
            || !(historyToggleButton instanceof HTMLButtonElement)
            || !(historyPanel instanceof HTMLElement)
            || !(historyList instanceof HTMLElement)
            || !(historyClearButton instanceof HTMLButtonElement)
            || !(panel instanceof HTMLElement)
            || !(form instanceof HTMLFormElement)
            || !(input instanceof HTMLTextAreaElement)
            || !(sendButton instanceof HTMLButtonElement)
            || !(attachButton instanceof HTMLButtonElement)
            || !(imageInput instanceof HTMLInputElement)
            || !(imagePreview instanceof HTMLElement)
            || !(imageThumbnail instanceof HTMLImageElement)
            || !(imageName instanceof HTMLElement)
            || !(imageRemoveButton instanceof HTMLButtonElement)
            || !(messages instanceof HTMLElement)) {
        return;
    }

    function storageGet(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (error) {
            return null;
        }
    }

    function storageSet(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch (error) {
            return false;
        }

        return true;
    }

    function createConversation() {
        const now = new Date().toISOString();
        return {
            id: `chat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            title: "New chat",
            createdAt: now,
            updatedAt: now,
            messages: []
        };
    }

    function normalizeConversation(value) {
        if (!value || typeof value !== "object" || !Array.isArray(value.messages)) {
            return null;
        }

        const id = typeof value.id === "string" && value.id ? value.id : createConversation().id;
        const createdAt = typeof value.createdAt === "string" ? value.createdAt : new Date().toISOString();
        const updatedAt = typeof value.updatedAt === "string" ? value.updatedAt : createdAt;
        const messagesValue = value.messages
                .map(normalizeMessage)
                .filter(Boolean)
                .slice(-MAX_STORED_MESSAGES);
        return {
            id,
            title: titleFromMessages(messagesValue),
            createdAt,
            updatedAt,
            messages: messagesValue
        };
    }

    function normalizeMessage(value) {
        if (!value || typeof value !== "object") {
            return null;
        }

        const role = value.role === "user" ? "user" : "assistant";
        const content = String(value.content || "").trim();
        const hasImage = Boolean(value.hasImage);
        if (!content && !hasImage) {
            return null;
        }

        return { role, content, hasImage };
    }

    function loadConversations() {
        const raw = storageGet(CHAT_STORAGE_KEY);
        if (!raw) {
            return [];
        }

        try {
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }

            return parsed
                    .map(normalizeConversation)
                    .filter(Boolean)
                    .slice(0, MAX_STORED_CONVERSATIONS);
        } catch (error) {
            return [];
        }
    }

    function serializableConversation(conversation) {
        return {
            id: conversation.id,
            title: titleFromMessages(conversation.messages),
            createdAt: conversation.createdAt,
            updatedAt: conversation.updatedAt,
            messages: conversation.messages
                    .slice(-MAX_STORED_MESSAGES)
                    .map((message) => ({
                        role: message.role,
                        content: message.content,
                        hasImage: Boolean(message.hasImage)
                    }))
        };
    }

    function saveConversations() {
        const ordered = conversations
                .slice()
                .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
                .slice(0, MAX_STORED_CONVERSATIONS);
        conversations = ordered;
        storageSet(CHAT_STORAGE_KEY, JSON.stringify(ordered.map(serializableConversation)));
        storageSet(ACTIVE_CHAT_KEY, activeConversationId || "");
    }

    function activeConversation() {
        let conversation = conversations.find((item) => item.id === activeConversationId);
        if (!conversation) {
            conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            saveConversations();
        }

        return conversation;
    }

    function ensureActiveConversation() {
        if (!conversations.length) {
            const conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            saveConversations();
            return conversation;
        }

        const existing = conversations.find((conversation) => conversation.id === activeConversationId);
        if (existing) {
            return existing;
        }

        activeConversationId = conversations[0].id;
        saveConversations();
        return conversations[0];
    }

    function csrfHeaders() {
        const token = document.querySelector("meta[name='_csrf']")?.getAttribute("content");
        const header = document.querySelector("meta[name='_csrf_header']")?.getAttribute("content");

        if (!token || !header) {
            return {};
        }

        return { [header]: token };
    }

    function setOpen(open) {
        widget.classList.toggle("open", open);
        panel.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));

        if (open) {
            input.focus();
        }
    }

    function setHistoryOpen(open) {
        historyPanel.hidden = !open;
        historyToggleButton.setAttribute("aria-expanded", String(open));
        historyToggleButton.setAttribute("aria-label", open ? "Close chat history" : "Open chat history");

        if (open) {
            renderHistoryList();
        }
    }

    function setBusy(busy) {
        widget.classList.toggle("is-busy", busy);
        input.disabled = busy;
        sendButton.disabled = busy;
        attachButton.disabled = busy;
        imageRemoveButton.disabled = busy;
        newChatButton.disabled = busy;
        historyToggleButton.disabled = busy;
        historyClearButton.disabled = busy;

        if (status instanceof HTMLElement) {
            status.textContent = busy ? translate("Thinking...") : "";
        }
    }

    function setStatus(message) {
        if (status instanceof HTMLElement) {
            status.textContent = message ? translate(message) : "";
        }
    }

    function renderConversation() {
        messages.innerHTML = "";
        const conversation = activeConversation();

        if (!conversation.messages.length) {
            renderMessage({
                role: "assistant",
                content: translate("Hi, what would you like help with?")
            });
            return;
        }

        conversation.messages.forEach(renderMessage);
    }

    function renderMessage(message) {
        const item = document.createElement("div");
        item.className = `chatbot-message ${message.role === "user" ? "user" : "assistant"}`;

        const bubble = document.createElement("div");
        bubble.className = "chatbot-bubble";

        if (message.imageDataUrl) {
            const image = document.createElement("img");
            image.className = "chatbot-message-image";
            image.src = message.imageDataUrl;
            image.alt = "";
            bubble.appendChild(image);
        } else if (message.hasImage) {
            const note = document.createElement("span");
            note.className = "chatbot-image-note";
            note.textContent = translate("Image attached");
            bubble.appendChild(note);
        }

        if (message.content) {
            const text = document.createElement("span");
            text.textContent = message.content;
            bubble.appendChild(text);
        }

        item.appendChild(bubble);
        messages.appendChild(item);
        messages.scrollTop = messages.scrollHeight;
    }

    function addConversationMessage(role, content, options = {}) {
        const conversation = activeConversation();
        const message = {
            role,
            content: String(content || "").trim(),
            hasImage: Boolean(options.hasImage)
        };

        if (options.imageDataUrl) {
            message.imageDataUrl = options.imageDataUrl;
        }

        conversation.messages.push(message);
        conversation.messages = conversation.messages.slice(-MAX_STORED_MESSAGES);
        conversation.title = titleFromMessages(conversation.messages);
        conversation.updatedAt = new Date().toISOString();
        saveConversations();
        renderMessage(message);
        renderHistoryList();
    }

    function titleFromMessages(messageList) {
        const userMessage = messageList.find((message) => message.role === "user" && (message.content || message.hasImage));
        if (!userMessage) {
            return "New chat";
        }

        const base = userMessage.content || "Image question";
        return truncate(base.replace(/\s+/g, " ").trim(), 42);
    }

    function modelHistorySnapshot() {
        return activeConversation().messages
                .filter((message) => message.content)
                .slice(-MAX_MODEL_HISTORY_MESSAGES)
                .map((message) => ({
                    role: message.role,
                    content: message.hasImage && message.role === "user"
                            ? `${message.content} [Image attached]`
                            : message.content
                }));
    }

    function formatDate(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return translate("Recently");
        }

        return new Intl.DateTimeFormat(undefined, {
            month: "short",
            day: "numeric",
            hour: "numeric",
            minute: "2-digit"
        }).format(date);
    }

    function renderHistoryList() {
        if (historyPanel.hidden) {
            return;
        }

        historyList.innerHTML = "";
        const saved = conversations
                .filter((conversation) => conversation.messages.length > 0)
                .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));

        if (!saved.length) {
            const empty = document.createElement("div");
            empty.className = "chatbot-history-empty";
            empty.textContent = translate("No saved chats yet.");
            historyList.appendChild(empty);
            return;
        }

        saved.forEach((conversation) => {
            const item = document.createElement("div");
            item.className = "chatbot-history-item";

            const loadButton = document.createElement("button");
            loadButton.type = "button";
            loadButton.className = "chatbot-history-load";
            loadButton.setAttribute("aria-label", `Open chat: ${conversation.title}`);

            const title = document.createElement("strong");
            title.textContent = conversation.title;
            const meta = document.createElement("span");
            meta.textContent = `${formatDate(conversation.updatedAt)} - ${conversation.messages.length} messages`;
            loadButton.append(title, meta);

            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "chatbot-icon-button chatbot-history-delete";
            deleteButton.setAttribute("aria-label", `Delete chat: ${conversation.title}`);
            deleteButton.title = "Delete";
            deleteButton.innerHTML = `
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3 6h18"></path>
                    <path d="M8 6V4h8v2"></path>
                    <path d="m19 6-1 14H6L5 6"></path>
                </svg>
            `;

            loadButton.addEventListener("click", () => {
                activeConversationId = conversation.id;
                storageSet(ACTIVE_CHAT_KEY, activeConversationId);
                setHistoryOpen(false);
                renderConversation();
                input.focus();
            });

            deleteButton.addEventListener("click", () => {
                deleteConversation(conversation.id);
            });

            item.append(loadButton, deleteButton);
            historyList.appendChild(item);
        });
    }

    function startNewChat() {
        const current = activeConversation();
        if (!current.messages.length) {
            setHistoryOpen(false);
            clearSelectedImage();
            renderConversation();
            input.focus();
            return;
        }

        const conversation = createConversation();
        conversations.unshift(conversation);
        activeConversationId = conversation.id;
        saveConversations();
        setHistoryOpen(false);
        clearSelectedImage();
        renderConversation();
        input.value = "";
        autoResize();
        input.focus();
    }

    function deleteConversation(conversationId) {
        conversations = conversations.filter((conversation) => conversation.id !== conversationId);

        if (activeConversationId === conversationId) {
            const conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            renderConversation();
        }

        if (!conversations.length) {
            const conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
        }

        saveConversations();
        renderHistoryList();
    }

    function clearHistory() {
        conversations = [createConversation()];
        activeConversationId = conversations[0].id;
        saveConversations();
        setHistoryOpen(false);
        clearSelectedImage();
        input.value = "";
        autoResize();
        renderConversation();
        input.focus();
    }

    function autoResize() {
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, 124)}px`;
    }

    function clearSelectedImage() {
        selectedImage = null;
        imageInput.value = "";
        imagePreview.hidden = true;
        imageThumbnail.removeAttribute("src");
        imageName.textContent = "";
    }

    function readAsDataUrl(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.addEventListener("load", () => resolve(String(reader.result || "")));
            reader.addEventListener("error", () => reject(new Error(translate("The image could not be read."))));
            reader.readAsDataURL(file);
        });
    }

    function loadImage(dataUrl) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.addEventListener("load", () => resolve(image));
            image.addEventListener("error", () => reject(new Error(translate("The image could not be read."))));
            image.src = dataUrl;
        });
    }

    function renderCanvas(image, maxSide) {
        const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
        const width = Math.max(1, Math.round(image.naturalWidth * scale));
        const height = Math.max(1, Math.round(image.naturalHeight * scale));
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;

        const context = canvas.getContext("2d");
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, width, height);
        context.drawImage(image, 0, 0, width, height);
        return canvas;
    }

    async function compressImage(file) {
        const source = await readAsDataUrl(file);
        const image = await loadImage(source);
        const maxSides = [1280, 960, 720, 560];
        const qualities = [0.82, 0.72, 0.62, 0.52, 0.44];

        for (const maxSide of maxSides) {
            const canvas = renderCanvas(image, maxSide);

            for (const quality of qualities) {
                const dataUrl = canvas.toDataURL("image/jpeg", quality);
                if (dataUrl.length <= MAX_IMAGE_DATA_URL_LENGTH) {
                    return {
                        dataUrl,
                        fileName: file.name || "uploaded-image.jpg",
                        mimeType: "image/jpeg"
                    };
                }
            }
        }

        throw new Error(translate("The image is too large. Please use a smaller image."));
    }

    function showSelectedImage(image) {
        selectedImage = image;
        imageThumbnail.src = image.dataUrl;
        imageName.textContent = image.fileName;
        imagePreview.hidden = false;
    }

    async function handleImageFile(file) {
        if (!file) {
            return;
        }

        if (!["image/png", "image/jpeg"].includes(file.type)) {
            setStatus("Please upload a PNG or JPG image.");
            return;
        }

        if (file.size > MAX_UPLOAD_BYTES) {
            setStatus("The image is too large. Please use a smaller image.");
            return;
        }

        setStatus("Preparing image...");

        try {
            showSelectedImage(await compressImage(file));
            setStatus("");
            setOpen(true);
            setHistoryOpen(false);
        } catch (error) {
            clearSelectedImage();
            setStatus(error.message || "The image could not be read.");
        }
    }

    async function ask(message, historySnapshot, image) {
        const payload = {
            message,
            history: historySnapshot,
            pageTitle: document.title,
            pagePath: window.location.pathname
        };

        if (image) {
            payload.imageDataUrl = image.dataUrl;
            payload.imageFileName = image.fileName;
            payload.imageMimeType = image.mimeType;
        }

        const response = await fetch("/api/chatbot/ask", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify(payload)
        });

        let body = null;
        try {
            body = await response.json();
        } catch (error) {
            body = null;
        }

        if (!response.ok || !body?.success) {
            throw new Error(body?.message || translate("The assistant is unavailable right now."));
        }

        return body.reply;
    }

    function truncate(value, maxLength) {
        if (!value || value.length <= maxLength) {
            return value;
        }

        return `${value.slice(0, Math.max(1, maxLength - 3))}...`;
    }

    toggle.addEventListener("click", () => {
        setOpen(!widget.classList.contains("open"));
    });

    closeButton.addEventListener("click", () => setOpen(false));

    newChatButton.addEventListener("click", startNewChat);

    historyToggleButton.addEventListener("click", () => {
        setOpen(true);
        setHistoryOpen(historyPanel.hidden);
    });

    historyClearButton.addEventListener("click", clearHistory);

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape" || !widget.classList.contains("open")) {
            return;
        }

        if (!historyPanel.hidden) {
            setHistoryOpen(false);
            return;
        }

        setOpen(false);
    });

    input.addEventListener("input", autoResize);

    attachButton.addEventListener("click", () => imageInput.click());

    imageInput.addEventListener("change", () => {
        handleImageFile(imageInput.files?.[0]);
    });

    imageRemoveButton.addEventListener("click", clearSelectedImage);

    input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        const imageToSend = selectedImage;
        const message = input.value.trim() || (imageToSend ? "Please analyze this image." : "");
        if (!message && !imageToSend) {
            return;
        }

        const historySnapshot = imageToSend ? [] : modelHistorySnapshot();
        setHistoryOpen(false);
        addConversationMessage("user", message, {
            hasImage: Boolean(imageToSend),
            imageDataUrl: imageToSend?.dataUrl
        });
        input.value = "";
        clearSelectedImage();
        autoResize();
        setBusy(true);

        try {
            const reply = await ask(message, historySnapshot, imageToSend);
            addConversationMessage("assistant", reply);
        } catch (error) {
            addConversationMessage("assistant", error.message || translate("The assistant is unavailable right now."));
        } finally {
            setBusy(false);
            input.focus();
        }
    });

    ensureActiveConversation();
    renderConversation();
    autoResize();
})();
