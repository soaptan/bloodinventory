(() => {
    const actionGroups = Array.from(document.querySelectorAll("[data-report-actions]"));
    const modal = document.querySelector("[data-report-download-modal]");
    const closeButtons = Array.from(document.querySelectorAll("[data-report-download-close]"));
    let lastTrigger = null;

    const closeActions = () => {
        actionGroups.forEach((group) => {
            group.classList.remove("open");
            group.querySelector("[data-report-actions-toggle]")?.setAttribute("aria-expanded", "false");
        });
    };

    const openModal = (trigger) => {
        if (!modal) {
            return;
        }

        lastTrigger = trigger || null;
        closeActions();
        modal.classList.add("open");
        modal.setAttribute("aria-hidden", "false");
        (modal.querySelector("select") || modal.querySelector("button, input"))?.focus();
    };

    const closeModal = ({ restoreFocus = true } = {}) => {
        if (!modal) {
            return;
        }

        modal.classList.remove("open");
        modal.setAttribute("aria-hidden", "true");

        if (restoreFocus) {
            lastTrigger?.focus();
        }

        lastTrigger = null;
    };

    const printPage = () => {
        closeActions();
        closeModal({ restoreFocus: false });
        window.requestAnimationFrame(() => window.print());
    };

    actionGroups.forEach((group) => {
        const toggle = group.querySelector("[data-report-actions-toggle]");
        const downloadOpen = group.querySelector("[data-report-download-open]");
        const printOpen = group.querySelector("[data-report-print]");

        group.addEventListener("click", (event) => event.stopPropagation());

        toggle?.addEventListener("click", () => {
            const isOpen = group.classList.toggle("open");
            toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
        });

        downloadOpen?.addEventListener("click", () => openModal(toggle));
        printOpen?.addEventListener("click", printPage);
    });

    closeButtons.forEach((button) => button.addEventListener("click", () => closeModal()));

    modal?.addEventListener("click", (event) => {
        if (event.target === modal) {
            closeModal();
        }
    });

    document.addEventListener("click", closeActions);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeActions();
            closeModal();
        }
    });
})();
