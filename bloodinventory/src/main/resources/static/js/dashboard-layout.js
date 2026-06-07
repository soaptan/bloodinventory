(() => {
    const STORAGE_KEY = "bloodinventory.dashboard.sidebarCollapsed";
    const THEME_STORAGE_KEY = "bloodinventory.dashboard.theme";
    const SEARCH_HISTORY_STORAGE_KEY = "bloodinventory.dashboard.searchHistory";
    const SEARCH_HISTORY_LIMIT = 6;
    const mobileQuery = window.matchMedia("(max-width: 920px)");
    const themeQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const TRANSLATABLE_ATTRIBUTES = ["placeholder", "aria-label", "title", "data-required-message"];
    const TRANSLATION_SKIP_TAGS = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE", "CODE", "PRE"]);
    const TRANSLATIONS = {
        zh: {
            "Blood Bank Digital Platform": "血库数字平台",
            "Blood Inventory Management System": "血液库存管理系统",
            "Blood Inventory System": "血液库存系统",
            "Secure workspace for blood inventory operations.": "血液库存操作的安全工作区。",
            "Search dashboard": "搜索仪表板",
            "Search dashboard or menu": "搜索仪表板或菜单",
            "Collapse menu": "收起菜单",
            "Expand menu": "展开菜单",
            "Open menu": "打开菜单",
            "Close menu": "关闭菜单",
            "Switch to light mode": "切换到浅色模式",
            "Switch to dark mode": "切换到深色模式",
            "Please select at least one option.": "请至少选择一个选项。",
            "Please fix the highlighted form fields.": "请修正高亮显示的表单字段。",
            "Main Navigation": "主导航",
            "Dashboard": "仪表板",
            "System overview": "系统概览",
            "Clinical overview": "临床概览",
            "Lab overview": "实验室概览",
            "My Profile": "我的资料",
            "View Profile": "查看资料",
            "Sign Out": "退出登录",
            "Notifications": "通知",
            "No notifications yet.": "暂无通知。",
            "Staff Management": "员工管理",
            "View all staff": "查看所有员工",
            "Staff Overview": "员工概览",
            "Add Staff": "新增员工",
            "Update Staff": "更新员工",
            "Delete Staff": "删除员工",
            "Create account": "创建账户",
            "Edit account": "编辑账户",
            "Remove account": "移除账户",
            "Storage Configuration": "存储配置",
            "Locations and capacity": "位置和容量",
            "Storage Overview": "存储概览",
            "Add Location": "新增位置",
            "Update Location": "更新位置",
            "Delete Location": "删除位置",
            "Create storage": "创建存储",
            "Edit storage": "编辑存储",
            "Remove storage": "移除存储",
            "Deferral Rules": "延期规则",
            "Eligibility controls": "资格控制",
            "Rule Overview": "规则概览",
            "Add Rule": "新增规则",
            "Update Rule": "更新规则",
            "Delete Rule": "删除规则",
            "Create rule": "创建规则",
            "Edit rule": "编辑规则",
            "Remove rule": "移除规则",
            "Inventory Monitoring": "库存监控",
            "Blood component status": "血液成分状态",
            "Inventory Overview": "库存概览",
            "Monitor stock status": "监控库存状态",
            "Reports and Alerts": "报告和警报",
            "Summaries and warnings": "摘要和警告",
            "Settings": "设置",
            "UI, backup and security": "界面、备份和安全",
            "Donor Eligibility": "献血者资格",
            "Assessment workflow": "评估流程",
            "Donor Overview": "献血者概览",
            "Add Donor": "新增献血者",
            "Update Donor": "更新献血者",
            "Delete Donor": "删除献血者",
            "Create record": "创建记录",
            "Edit record": "编辑记录",
            "Remove record": "移除记录",
            "Blood Collection": "血液采集",
            "Donation sessions": "献血场次",
            "Collection Overview": "采集概览",
            "Add Donation": "新增献血",
            "Update Donation": "更新献血",
            "Delete Donation": "删除献血",
            "Create session": "创建场次",
            "Edit session": "编辑场次",
            "Remove session": "移除场次",
            "Transfusion Request": "输血请求",
            "Clinical requests": "临床请求",
            "Request Overview": "请求概览",
            "Add Event": "新增事件",
            "Update Event": "更新事件",
            "Delete Event": "删除事件",
            "Record use": "记录使用",
            "Edit patient info": "编辑患者信息",
            "Reverse record": "撤销记录",
            "Safe Blood Match": "安全血液匹配",
            "Compatibility review": "兼容性审查",
            "Match Overview": "匹配概览",
            "Find Match": "查找匹配",
            "Reserve Unit": "预留单位",
            "Release Unit": "释放单位",
            "Filter stock": "筛选库存",
            "Hold stock": "保留库存",
            "Return stock": "退回库存",
            "Pending Test Queue": "待检队列",
            "Samples awaiting review": "等待审核的样本",
            "Queue Overview": "队列概览",
            "View Queue": "查看队列",
            "Update Queue": "更新队列",
            "Pending samples": "待处理样本",
            "Approve safe": "批准安全",
            "TTI Screening": "TTI 筛查",
            "Infection screening": "感染筛查",
            "Screening Overview": "筛查概览",
            "Add Result": "新增结果",
            "Update Result": "更新结果",
            "Delete Result": "删除结果",
            "Record screening": "记录筛查",
            "Edit screening": "编辑筛查",
            "Remove screening": "移除筛查",
            "Component Status": "成分状态",
            "Validation progress": "验证进度",
            "Status Overview": "状态概览",
            "View Components": "查看成分",
            "Update Status": "更新状态",
            "Audit Trail": "审计记录",
            "Tracked units": "追踪单位",
            "Release/discard": "放行/废弃",
            "Trace changes": "追踪变更",
            "Traceability": "追溯",
            "Unit movement tracking": "单位流向追踪",
            "System Settings": "系统设置",
            "Administrative workspace for interface, backup, language, and security settings.": "管理界面、备份、语言和安全设置的工作区。",
            "Customize the application interface, backup policy, default language, and security controls.": "自定义应用界面、备份策略、默认语言和安全控制。",
            "Interface": "界面",
            "Language": "语言",
            "Database Backup": "数据库备份",
            "Security": "安全",
            "Font scale and primary accent color.": "字体大小和主强调色。",
            "Appearance Controls": "外观控制",
            "Adjust the readable scale and choose a clinical accent color.": "调整可读字号并选择临床强调色。",
            "Font Size": "字体大小",
            "Accent Color": "强调色",
            "Hospital Color Palettes": "医院配色",
            "Crimson": "猩红",
            "Trust Blue": "信任蓝",
            "Teal": "青绿色",
            "Emerald": "翡翠绿",
            "Violet": "紫色",
            "System Logo": "系统标志",
            "Upload Logo": "上传标志",
            "Drop a PNG or JPG here, or choose a file. Recommended size is 200 x 50px.": "将 PNG 或 JPG 拖到此处，或选择文件。建议尺寸为 200 x 50px。",
            "Live UI Preview": "实时界面预览",
            "See the selected accent and font scale before saving.": "保存前预览强调色和字体大小。",
            "Administrator workspace": "管理员工作区",
            "Clinical teams will see this typography scale and accent treatment across navigation, cards, and actions.": "临床团队会在导航、卡片和操作中看到此字号和强调色。",
            "Primary Button": "主按钮",
            "Active navigation and focus states use this accent.": "当前导航和焦点状态使用此强调色。",
            "Preview updates instantly. Saving applies the interface preference across the application.": "预览会即时更新。保存后界面偏好将应用到整个系统。",
            "Save Interface": "保存界面",
            "Default system language preference.": "默认系统语言偏好。",
            "English": "英语",
            "Bahasa Malaysia": "马来语",
            "Chinese": "中文",
            "Save Language": "保存语言",
            "Manual backup and automated schedule.": "手动备份和自动计划。",
            "Run Manual Backup": "运行手动备份",
            "Automatic Backup": "自动备份",
            "Scheduled database backup": "计划数据库备份",
            "Backup Directory": "备份目录",
            "Frequency": "频率",
            "Daily": "每日",
            "Weekly": "每周",
            "Schedule Time": "计划时间",
            "Retention Days": "保留天数",
            "Save Backup Policy": "保存备份策略",
            "Backup History": "备份历史",
            "No backup records yet.": "暂无备份记录。",
            "ID": "编号",
            "Type": "类型",
            "Status": "状态",
            "Started": "开始时间",
            "File": "文件",
            "Message": "消息",
            "Action": "操作",
            "Download": "下载",
            "Session control and role access policy.": "会话控制和角色访问策略。",
            "Session Control": "会话控制",
            "Track active staff login sessions": "跟踪活跃员工登录会话",
            "Block New Login When Limit Reached": "达到限制时阻止新登录",
            "Keep the existing active session": "保留现有活跃会话",
            "Role Based Module Access": "基于角色的模块访问",
            "Restrict modules by staff type": "按员工类型限制模块",
            "Max Active Sessions": "最大活跃会话数",
            "Session Timeout Minutes": "会话超时分钟数",
            "Save Security": "保存安全设置",
            "Language setting saved.": "语言设置已保存。",
            "Interface settings saved.": "界面设置已保存。",
            "Backup schedule saved.": "备份计划已保存。",
            "Security settings saved.": "安全设置已保存。",
            "Administrator Dashboard": "管理员仪表板",
            "Medical Staff Dashboard": "医务人员仪表板",
            "Lab Technician Dashboard": "实验室技术员仪表板",
            "Total Staff": "员工总数",
            "Total Donors": "献血者总数",
            "Available Components": "可用成分",
            "Total Donations": "献血总数",
            "Near Expiry": "即将过期",
            "Total Components": "成分总数",
            "Eligible Donors": "合格献血者",
            "Deferred Donors": "延期献血者",
            "Transfusion Events": "输血事件",
            "Pending Tests": "待检项目",
            "Completed Tests": "已完成检测",
            "Safe Components": "安全成分",
            "Discarded Components": "废弃成分",
            "AVAILABLE": "可用",
            "RESERVED": "已预留",
            "ACTIVE": "启用",
            "INACTIVE": "停用",
            "QUARANTINED": "隔离",
            "DISCARDED": "已废弃",
            "EXPIRED": "已过期",
            "USED": "已使用",
            "PENDING": "待处理",
            "PASSED": "通过",
            "FAILED": "未通过",
            "NEGATIVE": "阴性",
            "REACTIVE": "阳性",
            "MATCHED": "匹配",
            "SUCCESS": "成功",
            "FAILED": "失败",
            "RUNNING": "运行中",
            "MANUAL": "手动",
            "AUTO": "自动"
        },
        ms: {
            "Blood Bank Digital Platform": "Platform Digital Bank Darah",
            "Blood Inventory Management System": "Sistem Pengurusan Inventori Darah",
            "Blood Inventory System": "Sistem Inventori Darah",
            "Secure workspace for blood inventory operations.": "Ruang kerja selamat untuk operasi inventori darah.",
            "Search dashboard": "Cari papan pemuka",
            "Search dashboard or menu": "Cari papan pemuka atau menu",
            "Collapse menu": "Tutup menu",
            "Expand menu": "Kembangkan menu",
            "Open menu": "Buka menu",
            "Close menu": "Tutup menu",
            "Switch to light mode": "Tukar ke mod cerah",
            "Switch to dark mode": "Tukar ke mod gelap",
            "Please select at least one option.": "Sila pilih sekurang-kurangnya satu pilihan.",
            "Please fix the highlighted form fields.": "Sila betulkan medan borang yang ditandakan.",
            "Main Navigation": "Navigasi Utama",
            "Dashboard": "Papan Pemuka",
            "System overview": "Gambaran sistem",
            "Clinical overview": "Gambaran klinikal",
            "Lab overview": "Gambaran makmal",
            "My Profile": "Profil Saya",
            "View Profile": "Lihat Profil",
            "Sign Out": "Log Keluar",
            "Notifications": "Pemberitahuan",
            "No notifications yet.": "Tiada pemberitahuan lagi.",
            "Staff Management": "Pengurusan Staf",
            "View all staff": "Lihat semua staf",
            "Staff Overview": "Gambaran Staf",
            "Add Staff": "Tambah Staf",
            "Update Staff": "Kemas Kini Staf",
            "Delete Staff": "Padam Staf",
            "Create account": "Cipta akaun",
            "Edit account": "Edit akaun",
            "Remove account": "Buang akaun",
            "Storage Configuration": "Konfigurasi Storan",
            "Locations and capacity": "Lokasi dan kapasiti",
            "Storage Overview": "Gambaran Storan",
            "Add Location": "Tambah Lokasi",
            "Update Location": "Kemas Kini Lokasi",
            "Delete Location": "Padam Lokasi",
            "Create storage": "Cipta storan",
            "Edit storage": "Edit storan",
            "Remove storage": "Buang storan",
            "Deferral Rules": "Peraturan Penangguhan",
            "Eligibility controls": "Kawalan kelayakan",
            "Rule Overview": "Gambaran Peraturan",
            "Add Rule": "Tambah Peraturan",
            "Update Rule": "Kemas Kini Peraturan",
            "Delete Rule": "Padam Peraturan",
            "Create rule": "Cipta peraturan",
            "Edit rule": "Edit peraturan",
            "Remove rule": "Buang peraturan",
            "Inventory Monitoring": "Pemantauan Inventori",
            "Blood component status": "Status komponen darah",
            "Inventory Overview": "Gambaran Inventori",
            "Monitor stock status": "Pantau status stok",
            "Reports and Alerts": "Laporan dan Amaran",
            "Summaries and warnings": "Ringkasan dan amaran",
            "Settings": "Tetapan",
            "UI, backup and security": "UI, sandaran dan keselamatan",
            "Donor Eligibility": "Kelayakan Penderma",
            "Assessment workflow": "Aliran penilaian",
            "Donor Overview": "Gambaran Penderma",
            "Add Donor": "Tambah Penderma",
            "Update Donor": "Kemas Kini Penderma",
            "Delete Donor": "Padam Penderma",
            "Create record": "Cipta rekod",
            "Edit record": "Edit rekod",
            "Remove record": "Buang rekod",
            "Blood Collection": "Pengumpulan Darah",
            "Donation sessions": "Sesi derma",
            "Collection Overview": "Gambaran Pengumpulan",
            "Add Donation": "Tambah Derma",
            "Update Donation": "Kemas Kini Derma",
            "Delete Donation": "Padam Derma",
            "Create session": "Cipta sesi",
            "Edit session": "Edit sesi",
            "Remove session": "Buang sesi",
            "Transfusion Request": "Permintaan Transfusi",
            "Clinical requests": "Permintaan klinikal",
            "Request Overview": "Gambaran Permintaan",
            "Add Event": "Tambah Peristiwa",
            "Update Event": "Kemas Kini Peristiwa",
            "Delete Event": "Padam Peristiwa",
            "Record use": "Rekod penggunaan",
            "Edit patient info": "Edit maklumat pesakit",
            "Reverse record": "Balikkan rekod",
            "Safe Blood Match": "Padanan Darah Selamat",
            "Compatibility review": "Semakan keserasian",
            "Match Overview": "Gambaran Padanan",
            "Find Match": "Cari Padanan",
            "Reserve Unit": "Rizab Unit",
            "Release Unit": "Lepaskan Unit",
            "Filter stock": "Tapis stok",
            "Hold stock": "Tahan stok",
            "Return stock": "Pulangkan stok",
            "Pending Test Queue": "Giliran Ujian Tertunda",
            "Samples awaiting review": "Sampel menunggu semakan",
            "Queue Overview": "Gambaran Giliran",
            "View Queue": "Lihat Giliran",
            "Update Queue": "Kemas Kini Giliran",
            "Pending samples": "Sampel tertunda",
            "Approve safe": "Luluskan selamat",
            "TTI Screening": "Saringan TTI",
            "Infection screening": "Saringan jangkitan",
            "Screening Overview": "Gambaran Saringan",
            "Add Result": "Tambah Keputusan",
            "Update Result": "Kemas Kini Keputusan",
            "Delete Result": "Padam Keputusan",
            "Record screening": "Rekod saringan",
            "Edit screening": "Edit saringan",
            "Remove screening": "Buang saringan",
            "Component Status": "Status Komponen",
            "Validation progress": "Kemajuan pengesahan",
            "Status Overview": "Gambaran Status",
            "View Components": "Lihat Komponen",
            "Update Status": "Kemas Kini Status",
            "Audit Trail": "Jejak Audit",
            "Tracked units": "Unit dijejaki",
            "Release/discard": "Lepas/buang",
            "Trace changes": "Jejak perubahan",
            "Traceability": "Kebolehkesanan",
            "Unit movement tracking": "Jejak pergerakan unit",
            "System Settings": "Tetapan Sistem",
            "Administrative workspace for interface, backup, language, and security settings.": "Ruang kerja pentadbiran untuk tetapan antara muka, sandaran, bahasa dan keselamatan.",
            "Customize the application interface, backup policy, default language, and security controls.": "Sesuaikan antara muka aplikasi, polisi sandaran, bahasa lalai dan kawalan keselamatan.",
            "Interface": "Antara Muka",
            "Language": "Bahasa",
            "Database Backup": "Sandaran Pangkalan Data",
            "Security": "Keselamatan",
            "Font scale and primary accent color.": "Skala fon dan warna aksen utama.",
            "Appearance Controls": "Kawalan Paparan",
            "Adjust the readable scale and choose a clinical accent color.": "Laraskan skala bacaan dan pilih warna aksen klinikal.",
            "Font Size": "Saiz Fon",
            "Accent Color": "Warna Aksen",
            "Hospital Color Palettes": "Palet Warna Hospital",
            "Crimson": "Merah",
            "Trust Blue": "Biru",
            "Teal": "Teal",
            "Emerald": "Hijau Zamrud",
            "Violet": "Ungu",
            "System Logo": "Logo Sistem",
            "Upload Logo": "Muat Naik Logo",
            "Drop a PNG or JPG here, or choose a file. Recommended size is 200 x 50px.": "Lepaskan PNG atau JPG di sini, atau pilih fail. Saiz disyorkan ialah 200 x 50px.",
            "Live UI Preview": "Pratonton UI Langsung",
            "See the selected accent and font scale before saving.": "Lihat aksen dan skala fon yang dipilih sebelum menyimpan.",
            "Administrator workspace": "Ruang kerja pentadbir",
            "Clinical teams will see this typography scale and accent treatment across navigation, cards, and actions.": "Pasukan klinikal akan melihat skala tipografi dan aksen ini pada navigasi, kad dan tindakan.",
            "Primary Button": "Butang Utama",
            "Active navigation and focus states use this accent.": "Navigasi aktif dan keadaan fokus menggunakan aksen ini.",
            "Preview updates instantly. Saving applies the interface preference across the application.": "Pratonton dikemas kini serta-merta. Simpan untuk menerapkan pilihan antara muka ke seluruh aplikasi.",
            "Save Interface": "Simpan Antara Muka",
            "Default system language preference.": "Pilihan bahasa sistem lalai.",
            "English": "Inggeris",
            "Bahasa Malaysia": "Bahasa Malaysia",
            "Chinese": "Cina",
            "Save Language": "Simpan Bahasa",
            "Manual backup and automated schedule.": "Sandaran manual dan jadual automatik.",
            "Run Manual Backup": "Jalankan Sandaran Manual",
            "Automatic Backup": "Sandaran Automatik",
            "Scheduled database backup": "Sandaran pangkalan data berjadual",
            "Backup Directory": "Direktori Sandaran",
            "Frequency": "Kekerapan",
            "Daily": "Harian",
            "Weekly": "Mingguan",
            "Schedule Time": "Masa Jadual",
            "Retention Days": "Hari Pengekalan",
            "Save Backup Policy": "Simpan Polisi Sandaran",
            "Backup History": "Sejarah Sandaran",
            "No backup records yet.": "Tiada rekod sandaran lagi.",
            "ID": "ID",
            "Type": "Jenis",
            "Status": "Status",
            "Started": "Dimulakan",
            "File": "Fail",
            "Message": "Mesej",
            "Action": "Tindakan",
            "Download": "Muat Turun",
            "Session control and role access policy.": "Kawalan sesi dan polisi akses peranan.",
            "Session Control": "Kawalan Sesi",
            "Track active staff login sessions": "Jejak sesi log masuk staf aktif",
            "Block New Login When Limit Reached": "Sekat Log Masuk Baharu Apabila Had Dicapai",
            "Keep the existing active session": "Kekalkan sesi aktif sedia ada",
            "Role Based Module Access": "Akses Modul Berdasarkan Peranan",
            "Restrict modules by staff type": "Hadkan modul mengikut jenis staf",
            "Max Active Sessions": "Sesi Aktif Maksimum",
            "Session Timeout Minutes": "Minit Tamat Masa Sesi",
            "Save Security": "Simpan Keselamatan",
            "Language setting saved.": "Tetapan bahasa disimpan.",
            "Interface settings saved.": "Tetapan antara muka disimpan.",
            "Backup schedule saved.": "Jadual sandaran disimpan.",
            "Security settings saved.": "Tetapan keselamatan disimpan.",
            "Administrator Dashboard": "Papan Pemuka Pentadbir",
            "Medical Staff Dashboard": "Papan Pemuka Staf Perubatan",
            "Lab Technician Dashboard": "Papan Pemuka Juruteknik Makmal",
            "Total Staff": "Jumlah Staf",
            "Total Donors": "Jumlah Penderma",
            "Available Components": "Komponen Tersedia",
            "Total Donations": "Jumlah Derma",
            "Near Expiry": "Hampir Luput",
            "Total Components": "Jumlah Komponen",
            "Eligible Donors": "Penderma Layak",
            "Deferred Donors": "Penderma Ditangguh",
            "Transfusion Events": "Peristiwa Transfusi",
            "Pending Tests": "Ujian Tertunda",
            "Completed Tests": "Ujian Selesai",
            "Safe Components": "Komponen Selamat",
            "Discarded Components": "Komponen Dibuang",
            "AVAILABLE": "TERSEDIA",
            "RESERVED": "DIRIZAB",
            "ACTIVE": "AKTIF",
            "INACTIVE": "TIDAK AKTIF",
            "QUARANTINED": "DIKUARANTIN",
            "DISCARDED": "DIBUANG",
            "EXPIRED": "LUPUT",
            "USED": "DIGUNAKAN",
            "PENDING": "TERTUNDA",
            "PASSED": "LULUS",
            "FAILED": "GAGAL",
            "NEGATIVE": "NEGATIF",
            "REACTIVE": "REAKTIF",
            "MATCHED": "SEPADAN",
            "SUCCESS": "BERJAYA",
            "RUNNING": "SEDANG BERJALAN",
            "MANUAL": "MANUAL",
            "AUTO": "AUTO"
        }
    };

    function currentLanguage() {
        return document.body?.dataset.language || document.documentElement.lang || "en";
    }

    function translateText(text, language = currentLanguage()) {
        const dictionary = TRANSLATIONS[language];
        const value = String(text ?? "");

        if (!dictionary || !value) {
            return value;
        }

        const compactValue = value.replace(/\s+/g, " ").trim();
        return dictionary[value] || dictionary[compactValue] || value;
    }

    function applyLanguageToTextNodes(root, language) {
        if (!root || !TRANSLATIONS[language]) {
            return;
        }

        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode(node) {
                const parent = node.parentElement;

                if (!parent || TRANSLATION_SKIP_TAGS.has(parent.tagName) || parent.closest("[data-no-translate]")) {
                    return NodeFilter.FILTER_REJECT;
                }

                return node.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }
        });

        const nodes = [];
        while (walker.nextNode()) {
            nodes.push(walker.currentNode);
        }

        nodes.forEach((node) => {
            const rawValue = node.nodeValue;
            const trimmedValue = rawValue.trim();
            const translated = translateText(trimmedValue, language);

            if (translated !== trimmedValue) {
                node.nodeValue = rawValue.replace(trimmedValue, translated);
            }
        });
    }

    function applyLanguageToAttributes(root, language) {
        if (!root || !TRANSLATIONS[language]) {
            return;
        }

        root.querySelectorAll("*").forEach((element) => {
            if (TRANSLATION_SKIP_TAGS.has(element.tagName) || element.closest("[data-no-translate]")) {
                return;
            }

            TRANSLATABLE_ATTRIBUTES.forEach((attribute) => {
                if (!element.hasAttribute(attribute)) {
                    return;
                }

                const value = element.getAttribute(attribute);
                const translated = translateText(value, language);

                if (translated !== value) {
                    element.setAttribute(attribute, translated);
                }
            });
        });
    }

    function applyLanguage() {
        const language = currentLanguage();

        if (!TRANSLATIONS[language]) {
            return;
        }

        document.documentElement.lang = language;
        document.title = document.title
            .split(" - ")
            .map((part) => translateText(part, language))
            .join(" - ");
        applyLanguageToTextNodes(document.body, language);
        applyLanguageToAttributes(document.body, language);
    }

    window.BloodInventoryTranslate = translateText;

    function readStoredCollapsed() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) === "true";
        } catch (error) {
            return false;
        }
    }

    function writeStoredCollapsed(collapsed) {
        try {
            window.localStorage.setItem(STORAGE_KEY, String(collapsed));
        } catch (error) {
            // Ignore storage failures and keep the UI working.
        }
    }

    function readStoredTheme() {
        try {
            const theme = window.localStorage.getItem(THEME_STORAGE_KEY);
            return theme === "dark" || theme === "light" ? theme : null;
        } catch (error) {
            return null;
        }
    }

    function writeStoredTheme(isDark) {
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, isDark ? "dark" : "light");
        } catch (error) {
            // Ignore storage failures and keep the UI working.
        }
    }

    function isDarkThemeActive() {
        const storedTheme = readStoredTheme();
        if (storedTheme !== null) {
            return storedTheme === "dark";
        }

        return themeQuery.matches;
    }

    function updateThemeButtons(isDark) {
        const nextLabel = translateText(isDark ? "Switch to light mode" : "Switch to dark mode");

        document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
            if (!(button instanceof HTMLButtonElement)) {
                return;
            }

            button.classList.toggle("is-dark", isDark);
            button.setAttribute("aria-pressed", String(isDark));
            button.setAttribute("aria-label", nextLabel);
            button.title = nextLabel;

            const labelTarget = button.querySelector("[data-theme-toggle-label]");
            if (labelTarget instanceof HTMLElement) {
                labelTarget.textContent = nextLabel;
            }
        });
    }

    function applyTheme(isDark) {
        document.body.classList.toggle("theme-dark", isDark);
        updateThemeButtons(isDark);
    }

    function initThemeToggle() {
        applyTheme(isDarkThemeActive());

        document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
            if (!(button instanceof HTMLButtonElement)) {
                return;
            }

            button.addEventListener("click", () => {
                const nextIsDark = !document.body.classList.contains("theme-dark");
                writeStoredTheme(nextIsDark);
                applyTheme(nextIsDark);
            });
        });
    }

    function updateButtons(app, expanded) {
        const nextLabel = translateText(mobileQuery.matches
            ? (expanded ? "Close menu" : "Open menu")
            : (expanded ? "Collapse menu" : "Expand menu"));

        app.querySelectorAll("[data-sidebar-toggle]").forEach((button) => {
            button.setAttribute("aria-expanded", String(expanded));
            button.setAttribute("aria-label", nextLabel);

            const labelTarget = button.querySelector("[data-sidebar-toggle-label]");
            if (labelTarget) {
                labelTarget.textContent = nextLabel;
            }
        });
    }

    function syncState(app) {
        if (mobileQuery.matches) {
            updateButtons(app, app.classList.contains("sidebar-open"));
            return;
        }

        updateButtons(app, !app.classList.contains("sidebar-collapsed"));
    }

    function applySearch(app, query) {
        const normalizedQuery = query.trim().toLowerCase();

        app.querySelectorAll(".sidebar .nav-menu li").forEach((item) => {
            const matches = !normalizedQuery || item.textContent.toLowerCase().includes(normalizedQuery);
            item.hidden = !matches;
        });

        app.querySelectorAll("[data-search-item]").forEach((item) => {
            const matches = !normalizedQuery || item.textContent.toLowerCase().includes(normalizedQuery);
            item.hidden = !matches;
        });

        app.querySelectorAll(".sidebar .nav-group").forEach((group) => {
            const hasVisibleItems = Array.from(group.querySelectorAll(".nav-menu li")).some((item) => !item.hidden);
            group.hidden = !hasVisibleItems;
        });

        app.querySelectorAll(".sidebar .nav-section").forEach((section) => {
            const hasVisibleGroups = Array.from(section.querySelectorAll(".nav-group")).some((group) => !group.hidden);
            section.hidden = !hasVisibleGroups;
        });
    }

    function setSmartSearchOpen(searchInput, resultsContainer, open) {
        resultsContainer.hidden = !open;
        searchInput.setAttribute("aria-expanded", String(open));
    }

    function clearSmartSearch(searchInput, resultsContainer) {
        resultsContainer.replaceChildren();
        setSmartSearchOpen(searchInput, resultsContainer, false);
    }

    function readSearchHistory() {
        try {
            const value = window.localStorage.getItem(SEARCH_HISTORY_STORAGE_KEY);
            const parsed = JSON.parse(value || "[]");

            if (!Array.isArray(parsed)) {
                return [];
            }

            return parsed
                .filter((item) => typeof item === "string")
                .map((item) => item.replace(/\s+/g, " ").trim())
                .filter((item) => item.length >= 2)
                .slice(0, SEARCH_HISTORY_LIMIT);
        } catch (error) {
            return [];
        }
    }

    function writeSearchHistory(history) {
        try {
            window.localStorage.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(history.slice(0, SEARCH_HISTORY_LIMIT)));
        } catch (error) {
            // Search history is a convenience feature; failing storage should not block search.
        }
    }

    function addSearchHistory(query) {
        const normalizedQuery = query.replace(/\s+/g, " ").trim();
        if (normalizedQuery.length < 2) {
            return;
        }

        const nextHistory = [
            normalizedQuery,
            ...readSearchHistory().filter((item) => item.toLowerCase() !== normalizedQuery.toLowerCase())
        ];
        writeSearchHistory(nextHistory);
    }

    function clearSearchHistory() {
        writeSearchHistory([]);
    }

    function deleteSearchHistoryItem(query) {
        const normalizedQuery = query.replace(/\s+/g, " ").trim().toLowerCase();
        const nextHistory = readSearchHistory()
            .filter((item) => item.toLowerCase() !== normalizedQuery);

        writeSearchHistory(nextHistory);
        return nextHistory;
    }

    function renderSearchHistory(searchInput, resultsContainer, onSelect) {
        const history = readSearchHistory();
        if (!history.length) {
            clearSmartSearch(searchInput, resultsContainer);
            return;
        }

        const container = document.createElement("div");
        container.className = "app-search-history";

        const header = document.createElement("div");
        header.className = "app-search-history-header";

        const title = document.createElement("span");
        title.textContent = translateText("Recent searches");

        const clearButton = document.createElement("button");
        clearButton.type = "button";
        clearButton.className = "app-search-history-clear";
        clearButton.textContent = translateText("Clear");
        clearButton.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            clearSearchHistory();
            clearSmartSearch(searchInput, resultsContainer);
            searchInput.focus();
        });

        header.append(title, clearButton);
        container.append(header);

        history.forEach((query) => {
            const row = document.createElement("div");
            row.className = "app-search-history-row";

            const button = document.createElement("button");
            button.type = "button";
            button.className = "app-search-history-item";
            button.textContent = query;
            button.addEventListener("click", () => onSelect(query));

            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "app-search-history-delete";
            deleteButton.setAttribute("aria-label", `Delete search history item: ${query}`);
            deleteButton.title = translateText("Delete");
            deleteButton.addEventListener("click", (event) => {
                event.preventDefault();
                event.stopPropagation();

                const nextHistory = deleteSearchHistoryItem(query);
                if (!nextHistory.length) {
                    clearSmartSearch(searchInput, resultsContainer);
                } else {
                    renderSearchHistory(searchInput, resultsContainer, onSelect);
                }

                searchInput.focus();
            });

            row.append(button, deleteButton);
            container.append(row);
        });

        resultsContainer.replaceChildren(container);
        setSmartSearchOpen(searchInput, resultsContainer, true);
    }

    function renderSmartSearchMessage(searchInput, resultsContainer, message, className = "app-search-empty") {
        const item = document.createElement("div");
        item.className = className;
        item.textContent = translateText(message);
        resultsContainer.replaceChildren(item);
        setSmartSearchOpen(searchInput, resultsContainer, true);
    }

    function renderSmartSearchResults(searchInput, resultsContainer, results, query) {
        if (!Array.isArray(results) || !results.length) {
            renderSmartSearchMessage(searchInput, resultsContainer, "No matching pages found.");
            return;
        }

        const list = document.createElement("div");
        list.className = "app-search-results-list";

        results.forEach((result) => {
            const link = document.createElement("a");
            link.className = "app-search-result";
            link.href = result.url || "#";
            link.addEventListener("click", () => addSearchHistory(query || searchInput.value));

            const title = document.createElement("span");
            title.className = "app-search-result-title";
            title.textContent = result.title || "Search result";

            const description = document.createElement("span");
            description.className = "app-search-result-description";
            description.textContent = result.description || "";

            const meta = document.createElement("span");
            meta.className = "app-search-result-meta";

            const category = document.createElement("span");
            category.textContent = result.category || "System";

            const matchType = document.createElement("span");
            matchType.textContent = result.matchType || "Smart match";

            meta.append(category, matchType);
            link.append(title, description, meta);
            list.append(link);
        });

        resultsContainer.replaceChildren(list);
        setSmartSearchOpen(searchInput, resultsContainer, true);
    }

    function initSearch(app) {
        const searchForm = app.querySelector("[data-dashboard-search]");
        const searchInput = app.querySelector("[data-dashboard-search-input]");
        const resultsContainer = app.querySelector("[data-smart-search-results]");

        if (!(searchForm instanceof HTMLFormElement)
                || !(searchInput instanceof HTMLInputElement)
                || !(resultsContainer instanceof HTMLElement)) {
            return;
        }

        let debounceId = 0;
        let searchController = null;

        const updateSearch = () => applySearch(app, searchInput.value);
        const selectHistoryQuery = (query) => {
            searchInput.value = query;
            updateSearch();
            performSmartSearch(query);
            searchInput.focus();
        };
        const showSearchHistory = () => renderSearchHistory(searchInput, resultsContainer, selectHistoryQuery);

        const abortSmartSearch = () => {
            if (searchController) {
                searchController.abort();
                searchController = null;
            }
        };

        const performSmartSearch = (query) => {
            const normalizedQuery = query.trim();

            abortSmartSearch();

            if (normalizedQuery.length < 2) {
                showSearchHistory();
                return;
            }

            searchController = new AbortController();
            renderSmartSearchMessage(searchInput, resultsContainer, "Searching...", "app-search-loading");

            fetch(`/api/smart-search?q=${encodeURIComponent(normalizedQuery)}`, {
                method: "GET",
                headers: {
                    Accept: "application/json"
                },
                signal: searchController.signal
            })
                .then((response) => {
                    if (!response.ok || response.redirected) {
                        throw new Error("Smart search is unavailable.");
                    }
                    return response.json();
                })
                .then((results) => {
                    if (searchInput.value.trim() !== normalizedQuery) {
                        return;
                    }

                    renderSmartSearchResults(searchInput, resultsContainer, results, normalizedQuery);
                })
                .catch((error) => {
                    if (error.name === "AbortError") {
                        return;
                    }

                    renderSmartSearchMessage(searchInput, resultsContainer, "Search is unavailable.");
                });
        };

        searchInput.addEventListener("input", () => {
            updateSearch();
            window.clearTimeout(debounceId);

            const query = searchInput.value.trim();
            if (query.length < 2) {
                abortSmartSearch();
                showSearchHistory();
                return;
            }

            debounceId = window.setTimeout(() => performSmartSearch(query), 260);
        });

        searchInput.addEventListener("focus", () => {
            if (resultsContainer.childElementCount > 0 && searchInput.value.trim().length >= 2) {
                setSmartSearchOpen(searchInput, resultsContainer, true);
                return;
            }

            if (searchInput.value.trim().length < 2) {
                showSearchHistory();
            }
        });

        searchForm.addEventListener("submit", (event) => {
            event.preventDefault();
            window.clearTimeout(debounceId);
            updateSearch();
            addSearchHistory(searchInput.value);
            performSmartSearch(searchInput.value);
        });

        document.addEventListener("click", (event) => {
            if (!searchForm.contains(event.target)) {
                setSmartSearchOpen(searchInput, resultsContainer, false);
            }
        });

        searchForm.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                abortSmartSearch();
                setSmartSearchOpen(searchInput, resultsContainer, false);
                searchInput.blur();
            }
        });
    }

    function initProfileMenu(app) {
        const menus = app.querySelectorAll("[data-profile-menu]");

        if (!menus.length) {
            return;
        }

        const closeMenus = () => {
            menus.forEach((menu) => {
                menu.classList.remove("open");

                const toggle = menu.querySelector("[data-profile-toggle]");
                if (toggle instanceof HTMLElement) {
                    toggle.setAttribute("aria-expanded", "false");
                }
            });
        };

        menus.forEach((menu) => {
            const toggle = menu.querySelector("[data-profile-toggle]");
            const panel = menu.querySelector("[data-profile-panel]");

            if (!(toggle instanceof HTMLButtonElement) || !(panel instanceof HTMLElement)) {
                return;
            }

            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                const willOpen = !menu.classList.contains("open");
                closeMenus();
                menu.classList.toggle("open", willOpen);
                toggle.setAttribute("aria-expanded", String(willOpen));
            });

            panel.addEventListener("click", (event) => {
                event.stopPropagation();
            });
        });

        document.addEventListener("click", closeMenus);

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeMenus();
            }
        });
    }

    function initNotificationMenu(app) {
        const menus = app.querySelectorAll("[data-notification-menu]");

        if (!menus.length) {
            return;
        }

        const closeMenus = () => {
            menus.forEach((menu) => {
                menu.classList.remove("open");

                const toggle = menu.querySelector("[data-notification-toggle]");
                if (toggle instanceof HTMLElement) {
                    toggle.setAttribute("aria-expanded", "false");
                }
            });
        };

        menus.forEach((menu) => {
            const toggle = menu.querySelector("[data-notification-toggle]");
            const panel = menu.querySelector("[data-notification-panel]");

            if (!(toggle instanceof HTMLButtonElement) || !(panel instanceof HTMLElement)) {
                return;
            }

            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                const willOpen = !menu.classList.contains("open");
                closeMenus();
                menu.classList.toggle("open", willOpen);
                toggle.setAttribute("aria-expanded", String(willOpen));
            });

            panel.addEventListener("click", (event) => {
                event.stopPropagation();
            });
        });

        document.addEventListener("click", closeMenus);

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeMenus();
            }
        });
    }

    function validateCheckboxGroup(group) {
        const checkboxes = Array.from(group.querySelectorAll("input[type='checkbox']"));
        const checked = checkboxes.some((checkbox) => checkbox.checked);
        const message = translateText(group.dataset.requiredMessage || "Please select at least one option.");

        checkboxes.forEach((checkbox, index) => {
            checkbox.setCustomValidity(checked || index !== 0 ? "" : message);
        });

        group.classList.toggle("is-invalid", !checked);
        return checked;
    }

    function initFrontendValidation(app) {
        app.querySelectorAll("form.needs-validation").forEach((form) => {
            if (!(form instanceof HTMLFormElement)) {
                return;
            }

            const checkboxGroups = Array.from(form.querySelectorAll("[data-required-checkbox-group]"));

            checkboxGroups.forEach((group) => {
                group.addEventListener("change", () => validateCheckboxGroup(group));
            });

            form.addEventListener("submit", (event) => {
                checkboxGroups.forEach(validateCheckboxGroup);

                if (!form.checkValidity()) {
                    event.preventDefault();
                    event.stopPropagation();

                    if (window.Toast) {
                        window.Toast.fire({
                            icon: "error",
                            title: translateText("Please fix the highlighted form fields.")
                        });
                    }
                }

                form.classList.add("was-validated");
            });
        });
    }

    function initDashboardApp(app) {
        if (!(app instanceof HTMLElement)) {
            return;
        }

        if (!mobileQuery.matches && readStoredCollapsed()) {
            app.classList.add("sidebar-collapsed");
        }

        syncState(app);
        initSearch(app);
        initProfileMenu(app);
        initNotificationMenu(app);
        initFrontendValidation(app);

        app.querySelectorAll("[data-sidebar-toggle]").forEach((button) => {
            button.addEventListener("click", () => {
                if (mobileQuery.matches) {
                    app.classList.toggle("sidebar-open");
                    syncState(app);
                    return;
                }

                const collapsed = app.classList.toggle("sidebar-collapsed");
                writeStoredCollapsed(collapsed);
                syncState(app);
            });
        });

        app.querySelectorAll("[data-sidebar-close]").forEach((button) => {
            button.addEventListener("click", () => {
                app.classList.remove("sidebar-open");
                syncState(app);
            });
        });
    }

    initThemeToggle();
    applyLanguage();

    document.querySelectorAll(".dashboard-app").forEach(initDashboardApp);

    mobileQuery.addEventListener("change", (event) => {
        document.querySelectorAll(".dashboard-app").forEach((app) => {
            if (!(app instanceof HTMLElement)) {
                return;
            }

            if (event.matches) {
                app.classList.remove("sidebar-collapsed");
            } else {
                app.classList.remove("sidebar-open");
                app.classList.toggle("sidebar-collapsed", readStoredCollapsed());
            }

            syncState(app);
        });
    });

    themeQuery.addEventListener("change", (event) => {
        if (readStoredTheme() !== null) {
            return;
        }

        applyTheme(event.matches);
    });
})();
