(() => {
    const modals = Array.from(document.querySelectorAll("[data-admin-create-modal]"));
    if (!modals.length) {
        return;
    }

    const syncBodyScroll = () => {
        document.body.style.overflow = modals.some((modal) => modal.classList.contains("is-open"))
            ? "hidden"
            : "";
    };

    modals.forEach((modal) => {
        const closeButton = modal.querySelector("[data-admin-create-close]");

        modal.addEventListener("click", (event) => {
            if (event.target === modal) {
                closeButton?.click();
            }
        });

        if (modal.classList.contains("is-open")) {
            modal.querySelector("input, select, textarea, button")?.focus({ preventScroll: true });
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") {
            return;
        }

        const openModal = modals.find((modal) => modal.classList.contains("is-open"));
        openModal?.querySelector("[data-admin-create-close]")?.click();
    });

    document.querySelectorAll("[data-lock-type-select]").forEach((select) => {
        const form = select.closest("form");
        const coolingInput = form?.querySelector("[data-cooling-days-input]");
        if (!coolingInput) {
            return;
        }

        const syncCoolingInput = () => {
            const isPermanent = select.value === "PERMANENT";
            coolingInput.disabled = isPermanent;
            coolingInput.required = !isPermanent;

            if (isPermanent) {
                coolingInput.value = "";
                coolingInput.removeAttribute("required");
                coolingInput.setAttribute("aria-disabled", "true");
                coolingInput.placeholder = "Not used for permanent";
                return;
            }

            coolingInput.setAttribute("required", "required");
            coolingInput.removeAttribute("aria-disabled");
            coolingInput.placeholder = "Enter days";
        };

        select.addEventListener("change", syncCoolingInput);
        syncCoolingInput();
    });

    syncBodyScroll();
})();
