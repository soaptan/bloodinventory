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
        const menu = group.querySelector("[data-report-actions-menu]");
        const menuItems = Array.from(menu?.querySelectorAll('[role="menuitem"]') || []);
        const downloadOpen = group.querySelector("[data-report-download-open]");
        const printOpen = group.querySelector("[data-report-print]");

        group.addEventListener("click", (event) => event.stopPropagation());

        toggle?.addEventListener("click", () => {
            const isOpen = group.classList.toggle("open");
            toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
        });

        toggle?.addEventListener("keydown", (event) => {
            if (event.key !== "ArrowDown" && event.key !== "ArrowUp") {
                return;
            }

            event.preventDefault();
            group.classList.add("open");
            toggle.setAttribute("aria-expanded", "true");
            const targetIndex = event.key === "ArrowUp" ? menuItems.length - 1 : 0;
            menuItems[targetIndex]?.focus();
        });

        menu?.addEventListener("keydown", (event) => {
            const currentIndex = menuItems.indexOf(document.activeElement);
            let nextIndex = currentIndex;

            if (event.key === "ArrowDown") {
                nextIndex = (currentIndex + 1) % menuItems.length;
            } else if (event.key === "ArrowUp") {
                nextIndex = (currentIndex - 1 + menuItems.length) % menuItems.length;
            } else if (event.key === "Home") {
                nextIndex = 0;
            } else if (event.key === "End") {
                nextIndex = menuItems.length - 1;
            } else {
                return;
            }

            event.preventDefault();
            menuItems[nextIndex]?.focus();
        });

        group.addEventListener("focusout", () => {
            window.setTimeout(() => {
                if (!group.contains(document.activeElement)) {
                    group.classList.remove("open");
                    toggle?.setAttribute("aria-expanded", "false");
                }
            }, 0);
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
            const openGroup = actionGroups.find((group) => group.classList.contains("open"));
            const modalWasOpen = modal?.classList.contains("open");
            closeActions();
            closeModal();
            if (!modalWasOpen) {
                openGroup?.querySelector("[data-report-actions-toggle]")?.focus();
            }
        }
    });
})();
