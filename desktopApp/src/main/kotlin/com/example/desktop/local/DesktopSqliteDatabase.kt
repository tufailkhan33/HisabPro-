package com.example.desktop.local

import com.example.shared.model.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Thread-safe parameterized SQLite persistence database for Windows Desktop HisabPro.
 * Uses WAL journal mode, strict prepared statements, offline financial queue protection,
 * encrypted credential/token storage, and SQLite backup/integrity checks.
 */
class DesktopSqliteDatabase(
    private val dbFile: File = File(DesktopCryptoHelper.resolveAppDataDir(), "hisabpro_desktop.db"),
    private val cryptoBaseDir: File = DesktopCryptoHelper.resolveAppDataDir()
) {
    private val lock = Any()

    init {
        dbFile.parentFile?.mkdirs()
        initializeSchema()
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        synchronized(lock) {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { st ->
                    st.execute("PRAGMA journal_mode=WAL;")
                    st.execute("PRAGMA foreign_keys=ON;")
                    st.execute("PRAGMA busy_timeout=5000;")
                }
                return block(conn)
            }
        }
    }

    private fun initializeSchema() = withConnection { conn ->
        conn.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS cached_session (
                    slot TEXT PRIMARY KEY,
                    encrypted_json TEXT NOT NULL,
                    updated_at_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS app_preferences (
                    pref_key TEXT PRIMARY KEY,
                    pref_value TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS businesses (
                    id TEXT PRIMARY KEY,
                    business_name TEXT NOT NULL,
                    business_code TEXT NOT NULL,
                    owner_uid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    owner_mobile TEXT NOT NULL,
                    owner_email TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    timezone TEXT NOT NULL,
                    allow_over_recovery INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    suspended_reason TEXT NOT NULL,
                    last_activity_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS business_settings (
                    business_id TEXT PRIMARY KEY,
                    id TEXT NOT NULL,
                    business_name TEXT NOT NULL,
                    business_address TEXT NOT NULL,
                    contact_phone TEXT NOT NULL,
                    allow_over_recovery INTEGER NOT NULL,
                    require_invoice_number INTEGER NOT NULL,
                    updated_by TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS staff_users (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    user_code TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    mobile_number TEXT NOT NULL,
                    email TEXT NOT NULL,
                    role TEXT NOT NULL,
                    status TEXT NOT NULL,
                    must_change_password INTEGER NOT NULL,
                    session_revoked_before_millis INTEGER NOT NULL,
                    last_login_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS permissions (
                    user_id TEXT NOT NULL,
                    business_id TEXT NOT NULL,
                    view_dashboard INTEGER NOT NULL,
                    view_shops INTEGER NOT NULL,
                    add_shops INTEGER NOT NULL,
                    edit_shops INTEGER NOT NULL,
                    add_credit INTEGER NOT NULL,
                    edit_credit INTEGER NOT NULL,
                    record_recovery INTEGER NOT NULL,
                    view_recovery_history INTEGER NOT NULL,
                    view_bookers INTEGER NOT NULL,
                    manage_bookers INTEGER NOT NULL,
                    view_reports INTEGER NOT NULL,
                    generate_pdfs INTEGER NOT NULL,
                    search_shops INTEGER NOT NULL,
                    view_activity_logs INTEGER NOT NULL,
                    PRIMARY KEY (business_id, user_id)
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS bookers (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    booker_code TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    mobile_number TEXT NOT NULL,
                    address TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    status TEXT NOT NULL,
                    must_change_password INTEGER NOT NULL,
                    session_revoked_before_millis INTEGER NOT NULL,
                    last_login_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS shops (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    booker_id TEXT NOT NULL,
                    booker_name TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    mobile_number TEXT NOT NULL,
                    address TEXT NOT NULL,
                    city TEXT NOT NULL,
                    opening_credit_paisa INTEGER NOT NULL,
                    opening_date_millis INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    is_archived INTEGER NOT NULL,
                    created_by_account_id TEXT NOT NULL,
                    created_by_name TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS products (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    sku TEXT NOT NULL,
                    name TEXT NOT NULL,
                    unit TEXT NOT NULL,
                    unit_price_paisa INTEGER NOT NULL,
                    stock_qty INTEGER NOT NULL,
                    reorder_level INTEGER NOT NULL,
                    is_archived INTEGER NOT NULL,
                    updated_at_millis INTEGER NOT NULL,
                    sync_status TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS bookings (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    booking_code TEXT NOT NULL,
                    shop_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    booker_id TEXT NOT NULL,
                    booker_name TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price_paisa INTEGER NOT NULL,
                    total_amount_paisa INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    booking_date_millis INTEGER NOT NULL,
                    booking_date_formatted TEXT NOT NULL,
                    created_by_account_id TEXT NOT NULL,
                    created_by_name TEXT NOT NULL,
                    sync_status TEXT NOT NULL,
                    idempotency_token TEXT NOT NULL,
                    device_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS credits (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    tx_code TEXT NOT NULL,
                    shop_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    booker_id TEXT NOT NULL,
                    booker_name TEXT NOT NULL,
                    amount_paisa INTEGER NOT NULL,
                    transaction_kind TEXT NOT NULL,
                    related_tx_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    invoice_number TEXT NOT NULL,
                    description TEXT NOT NULL,
                    attachment_uri TEXT NOT NULL,
                    tx_date_millis INTEGER NOT NULL,
                    tx_date_formatted TEXT NOT NULL,
                    tx_time_formatted TEXT NOT NULL,
                    created_by_account_id TEXT NOT NULL,
                    created_by_name TEXT NOT NULL,
                    created_by_role TEXT NOT NULL,
                    due_date_millis INTEGER NOT NULL,
                    due_date_formatted TEXT NOT NULL,
                    entry_timestamp_millis INTEGER NOT NULL,
                    entry_date_formatted TEXT NOT NULL,
                    entry_time_formatted TEXT NOT NULL,
                    sync_status TEXT NOT NULL,
                    idempotency_token TEXT NOT NULL,
                    device_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS recoveries (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    tx_code TEXT NOT NULL,
                    shop_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    booker_id TEXT NOT NULL,
                    booker_name TEXT NOT NULL,
                    amount_paisa INTEGER NOT NULL,
                    transaction_kind TEXT NOT NULL,
                    related_tx_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    payment_method TEXT NOT NULL,
                    reference_number TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    tx_date_millis INTEGER NOT NULL,
                    tx_date_formatted TEXT NOT NULL,
                    tx_time_formatted TEXT NOT NULL,
                    created_by_account_id TEXT NOT NULL,
                    created_by_name TEXT NOT NULL,
                    created_by_role TEXT NOT NULL,
                    correction_group_id TEXT NOT NULL,
                    corrected_from_tx_id TEXT NOT NULL,
                    original_amount_paisa INTEGER NOT NULL,
                    original_tx_date_millis INTEGER NOT NULL,
                    is_backdated INTEGER NOT NULL,
                    reversed_at_millis INTEGER NOT NULL,
                    reversed_by_account_id TEXT NOT NULL,
                    reversed_by_name TEXT NOT NULL,
                    sync_status TEXT NOT NULL,
                    idempotency_token TEXT NOT NULL,
                    device_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    activity_code TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    username TEXT NOT NULL,
                    role TEXT NOT NULL,
                    action TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    previous_value TEXT NOT NULL,
                    new_value TEXT NOT NULL,
                    date_formatted TEXT NOT NULL,
                    time_formatted TEXT NOT NULL,
                    timestamp_millis INTEGER NOT NULL,
                    description TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS notifications (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    category TEXT NOT NULL,
                    target_role TEXT NOT NULL,
                    target_booker_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    is_read INTEGER NOT NULL,
                    created_at_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_groups (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    member_ids_csv TEXT NOT NULL,
                    member_names_csv TEXT NOT NULL,
                    created_by_account_id TEXT NOT NULL,
                    created_by_name TEXT NOT NULL,
                    last_message_preview TEXT NOT NULL,
                    last_message_at_millis INTEGER NOT NULL,
                    is_archived INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    group_id TEXT NOT NULL,
                    sender_account_id TEXT NOT NULL,
                    sender_name TEXT NOT NULL,
                    sender_role TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    text TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    duration_sec INTEGER NOT NULL,
                    file_size_bytes INTEGER NOT NULL,
                    sent_at_millis INTEGER NOT NULL,
                    date_formatted TEXT NOT NULL,
                    time_formatted TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS pending_sync (
                    local_id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    username TEXT NOT NULL,
                    user_role TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    shop_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    booker_id TEXT NOT NULL,
                    booker_name TEXT NOT NULL,
                    tx_type TEXT NOT NULL,
                    amount_paisa INTEGER NOT NULL,
                    original_tx_date_millis INTEGER NOT NULL,
                    due_date_millis INTEGER NOT NULL,
                    invoice_or_ref_number TEXT NOT NULL,
                    payment_method TEXT NOT NULL,
                    description_or_notes TEXT NOT NULL,
                    local_device_timestamp INTEGER NOT NULL,
                    sync_status TEXT NOT NULL,
                    idempotency_token TEXT NOT NULL UNIQUE,
                    audit_metadata TEXT NOT NULL,
                    error_message TEXT NOT NULL,
                    retry_count INTEGER NOT NULL,
                    server_synced_at_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS sync_history (
                    id TEXT PRIMARY KEY,
                    business_id TEXT NOT NULL,
                    local_tx_id TEXT NOT NULL,
                    shop_uid TEXT NOT NULL,
                    tx_type TEXT NOT NULL,
                    amount_paisa INTEGER NOT NULL,
                    sync_status TEXT NOT NULL,
                    message TEXT NOT NULL,
                    synced_at_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    fun saveEncryptedSlot(slot: String, plainJson: String) = withConnection { conn ->
        val cipher = DesktopCryptoHelper.encrypt(plainJson, cryptoBaseDir)
        conn.prepareStatement(
            "INSERT INTO cached_session(slot, encrypted_json, updated_at_millis) VALUES(?,?,?) " +
                "ON CONFLICT(slot) DO UPDATE SET encrypted_json=excluded.encrypted_json, updated_at_millis=excluded.updated_at_millis"
        ).use { ps ->
            ps.setString(1, slot)
            ps.setString(2, cipher)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    fun loadEncryptedSlot(slot: String): String? = withConnection { conn ->
        conn.prepareStatement("SELECT encrypted_json FROM cached_session WHERE slot=?").use { ps ->
            ps.setString(1, slot)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    DesktopCryptoHelper.decrypt(rs.getString(1), cryptoBaseDir).ifBlank { null }
                } else null
            }
        }
    }

    fun clearEncryptedSlot(slot: String) = withConnection { conn ->
        conn.prepareStatement("DELETE FROM cached_session WHERE slot=?").use { ps ->
            ps.setString(1, slot)
            ps.executeUpdate()
        }
    }

    fun getPreference(key: String, default: String = ""): String = withConnection { conn ->
        conn.prepareStatement("SELECT pref_value FROM app_preferences WHERE pref_key=?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else default }
        }
    }

    fun setPreference(key: String, value: String) = withConnection { conn ->
        conn.prepareStatement(
            "INSERT INTO app_preferences(pref_key, pref_value) VALUES(?,?) " +
                "ON CONFLICT(pref_key) DO UPDATE SET pref_value=excluded.pref_value"
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    fun upsertBusiness(b: Business) = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT INTO businesses(id,business_name,business_code,owner_uid,owner_name,owner_mobile,owner_email,currency,timezone,allow_over_recovery,status,suspended_reason,last_activity_millis)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              business_name=excluded.business_name,business_code=excluded.business_code,owner_uid=excluded.owner_uid,
              owner_name=excluded.owner_name,owner_mobile=excluded.owner_mobile,owner_email=excluded.owner_email,
              currency=excluded.currency,timezone=excluded.timezone,allow_over_recovery=excluded.allow_over_recovery,
              status=excluded.status,suspended_reason=excluded.suspended_reason,last_activity_millis=excluded.last_activity_millis
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, b.id)
            ps.setString(2, b.businessName)
            ps.setString(3, b.businessCode)
            ps.setString(4, b.ownerUid)
            ps.setString(5, b.ownerName)
            ps.setString(6, b.ownerMobile)
            ps.setString(7, b.ownerEmail)
            ps.setString(8, b.currency)
            ps.setString(9, b.timezone)
            ps.setInt(10, if (b.allowOverRecovery) 1 else 0)
            ps.setString(11, b.status)
            ps.setString(12, b.suspendedReason)
            ps.setLong(13, b.lastActivityMillis)
            ps.executeUpdate()
        }
    }

    fun getBusiness(businessId: String): Business? = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM businesses WHERE id=?").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                Business(
                    id = rs.getString("id"),
                    businessName = rs.getString("business_name"),
                    businessCode = rs.getString("business_code"),
                    ownerUid = rs.getString("owner_uid"),
                    ownerName = rs.getString("owner_name"),
                    ownerMobile = rs.getString("owner_mobile"),
                    ownerEmail = rs.getString("owner_email"),
                    currency = rs.getString("currency"),
                    timezone = rs.getString("timezone"),
                    allowOverRecovery = rs.getInt("allow_over_recovery") == 1,
                    status = rs.getString("status"),
                    suspendedReason = rs.getString("suspended_reason"),
                    lastActivityMillis = rs.getLong("last_activity_millis")
                )
            }
        }
    }

    fun upsertSettings(s: BusinessSettings) = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT INTO business_settings(business_id,id,business_name,business_address,contact_phone,allow_over_recovery,require_invoice_number,updated_by)
            VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(business_id) DO UPDATE SET
              id=excluded.id,business_name=excluded.business_name,business_address=excluded.business_address,
              contact_phone=excluded.contact_phone,allow_over_recovery=excluded.allow_over_recovery,
              require_invoice_number=excluded.require_invoice_number,updated_by=excluded.updated_by
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, s.businessId)
            ps.setString(2, s.id.ifBlank { "default" })
            ps.setString(3, s.businessName)
            ps.setString(4, s.businessAddress)
            ps.setString(5, s.contactPhone)
            ps.setInt(6, if (s.allowOverRecovery) 1 else 0)
            ps.setInt(7, if (s.requireInvoiceNumber) 1 else 0)
            ps.setString(8, s.updatedBy)
            ps.executeUpdate()
        }
    }

    fun getSettings(businessId: String): BusinessSettings? = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM business_settings WHERE business_id=?").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                BusinessSettings(
                    id = rs.getString("id"),
                    businessId = rs.getString("business_id"),
                    businessName = rs.getString("business_name"),
                    businessAddress = rs.getString("business_address"),
                    contactPhone = rs.getString("contact_phone"),
                    allowOverRecovery = rs.getInt("allow_over_recovery") == 1,
                    requireInvoiceNumber = rs.getInt("require_invoice_number") == 1,
                    updatedBy = rs.getString("updated_by")
                )
            }
        }
    }

    fun replaceStaffUsers(businessId: String, items: List<BusinessUser>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM staff_users WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO staff_users(id,business_id,user_code,full_name,username,mobile_number,email,role,status,must_change_password,session_revoked_before_millis,last_login_millis) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { u ->
                    ps.setString(1, u.id); ps.setString(2, u.businessId.ifBlank { businessId })
                    ps.setString(3, u.userCode); ps.setString(4, u.fullName); ps.setString(5, u.username)
                    ps.setString(6, u.mobileNumber); ps.setString(7, u.email); ps.setString(8, u.role)
                    ps.setString(9, u.status); ps.setInt(10, if (u.mustChangePassword) 1 else 0)
                    ps.setLong(11, u.sessionRevokedBeforeMillis); ps.setLong(12, u.lastLoginMillis)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getStaffUsers(businessId: String): List<BusinessUser> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM staff_users WHERE business_id=? ORDER BY full_name").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            BusinessUser(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                userCode = rs.getString("user_code"),
                                fullName = rs.getString("full_name"),
                                username = rs.getString("username"),
                                mobileNumber = rs.getString("mobile_number"),
                                email = rs.getString("email"),
                                role = rs.getString("role"),
                                status = rs.getString("status"),
                                mustChangePassword = rs.getInt("must_change_password") == 1,
                                sessionRevokedBeforeMillis = rs.getLong("session_revoked_before_millis"),
                                lastLoginMillis = rs.getLong("last_login_millis")
                            )
                        )
                    }
                }
            }
        }
    }

    fun replacePermissions(businessId: String, items: List<StaffPermission>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM permissions WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO permissions(user_id,business_id,view_dashboard,view_shops,add_shops,edit_shops,add_credit,edit_credit,record_recovery,view_recovery_history,view_bookers,manage_bookers,view_reports,generate_pdfs,search_shops,view_activity_logs) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { p ->
                    ps.setString(1, p.userId.ifBlank { p.id })
                    ps.setString(2, p.businessId.ifBlank { businessId })
                    ps.setInt(3, if (p.viewDashboard) 1 else 0); ps.setInt(4, if (p.viewShops) 1 else 0)
                    ps.setInt(5, if (p.addShops) 1 else 0); ps.setInt(6, if (p.editShops) 1 else 0)
                    ps.setInt(7, if (p.addCredit) 1 else 0); ps.setInt(8, if (p.editCredit) 1 else 0)
                    ps.setInt(9, if (p.recordRecovery) 1 else 0); ps.setInt(10, if (p.viewRecoveryHistory) 1 else 0)
                    ps.setInt(11, if (p.viewBookers) 1 else 0); ps.setInt(12, if (p.manageBookers) 1 else 0)
                    ps.setInt(13, if (p.viewReports) 1 else 0); ps.setInt(14, if (p.generatePdfs) 1 else 0)
                    ps.setInt(15, if (p.searchShops) 1 else 0); ps.setInt(16, if (p.viewActivityLogs) 1 else 0)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getPermissions(businessId: String): List<StaffPermission> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM permissions WHERE business_id=?").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            StaffPermission(
                                id = rs.getString("user_id"),
                                businessId = rs.getString("business_id"),
                                userId = rs.getString("user_id"),
                                viewDashboard = rs.getInt("view_dashboard") == 1,
                                viewShops = rs.getInt("view_shops") == 1,
                                addShops = rs.getInt("add_shops") == 1,
                                editShops = rs.getInt("edit_shops") == 1,
                                addCredit = rs.getInt("add_credit") == 1,
                                editCredit = rs.getInt("edit_credit") == 1,
                                recordRecovery = rs.getInt("record_recovery") == 1,
                                viewRecoveryHistory = rs.getInt("view_recovery_history") == 1,
                                viewBookers = rs.getInt("view_bookers") == 1,
                                manageBookers = rs.getInt("manage_bookers") == 1,
                                viewReports = rs.getInt("view_reports") == 1,
                                generatePdfs = rs.getInt("generate_pdfs") == 1,
                                searchShops = rs.getInt("search_shops") == 1,
                                viewActivityLogs = rs.getInt("view_activity_logs") == 1
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceBookers(businessId: String, items: List<Booker>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM bookers WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO bookers(id,business_id,booker_code,full_name,username,mobile_number,address,notes,status,must_change_password,session_revoked_before_millis,last_login_millis) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { b ->
                    ps.setString(1, b.id); ps.setString(2, b.businessId.ifBlank { businessId })
                    ps.setString(3, b.bookerCode); ps.setString(4, b.fullName); ps.setString(5, b.username)
                    ps.setString(6, b.mobileNumber); ps.setString(7, b.address); ps.setString(8, b.notes)
                    ps.setString(9, b.status); ps.setInt(10, if (b.mustChangePassword) 1 else 0)
                    ps.setLong(11, b.sessionRevokedBeforeMillis); ps.setLong(12, b.lastLoginMillis)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getBookers(businessId: String): List<Booker> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM bookers WHERE business_id=? ORDER BY full_name").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Booker(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                bookerCode = rs.getString("booker_code"),
                                fullName = rs.getString("full_name"),
                                username = rs.getString("username"),
                                mobileNumber = rs.getString("mobile_number"),
                                address = rs.getString("address"),
                                notes = rs.getString("notes"),
                                status = rs.getString("status"),
                                mustChangePassword = rs.getInt("must_change_password") == 1,
                                sessionRevokedBeforeMillis = rs.getLong("session_revoked_before_millis"),
                                lastLoginMillis = rs.getLong("last_login_millis")
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceShops(businessId: String, items: List<Shop>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM shops WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO shops(id,business_id,shop_uid,shop_name,booker_id,booker_name,owner_name,mobile_number,address,city,opening_credit_paisa,opening_date_millis,notes,is_archived,created_by_account_id,created_by_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { s ->
                    ps.setString(1, s.id); ps.setString(2, s.businessId.ifBlank { businessId })
                    ps.setString(3, s.shopUid); ps.setString(4, s.shopName); ps.setString(5, s.bookerId)
                    ps.setString(6, s.bookerName); ps.setString(7, s.ownerName); ps.setString(8, s.mobileNumber)
                    ps.setString(9, s.address); ps.setString(10, s.city); ps.setLong(11, s.openingCreditPaisa)
                    ps.setLong(12, s.openingDateMillis); ps.setString(13, s.notes)
                    ps.setInt(14, if (s.isArchived) 1 else 0); ps.setString(15, s.createdByAccountId)
                    ps.setString(16, s.createdByName)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun upsertShop(s: Shop) = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT INTO shops(id,business_id,shop_uid,shop_name,booker_id,booker_name,owner_name,mobile_number,address,city,opening_credit_paisa,opening_date_millis,notes,is_archived,created_by_account_id,created_by_name)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              shop_name=excluded.shop_name,booker_id=excluded.booker_id,booker_name=excluded.booker_name,
              owner_name=excluded.owner_name,mobile_number=excluded.mobile_number,address=excluded.address,
              city=excluded.city,notes=excluded.notes,is_archived=excluded.is_archived
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, s.id); ps.setString(2, s.businessId)
            ps.setString(3, s.shopUid); ps.setString(4, s.shopName); ps.setString(5, s.bookerId)
            ps.setString(6, s.bookerName); ps.setString(7, s.ownerName); ps.setString(8, s.mobileNumber)
            ps.setString(9, s.address); ps.setString(10, s.city); ps.setLong(11, s.openingCreditPaisa)
            ps.setLong(12, s.openingDateMillis); ps.setString(13, s.notes)
            ps.setInt(14, if (s.isArchived) 1 else 0); ps.setString(15, s.createdByAccountId)
            ps.setString(16, s.createdByName)
            ps.executeUpdate()
        }
    }

    fun getShops(businessId: String, bookerFilterId: String? = null): List<Shop> = withConnection { conn ->
        val sql = if (bookerFilterId.isNullOrBlank()) {
            "SELECT * FROM shops WHERE business_id=? ORDER BY opening_date_millis DESC"
        } else {
            "SELECT * FROM shops WHERE business_id=? AND booker_id=? ORDER BY opening_date_millis DESC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, businessId)
            if (!bookerFilterId.isNullOrBlank()) ps.setString(2, bookerFilterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Shop(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                shopUid = rs.getString("shop_uid"),
                                shopName = rs.getString("shop_name"),
                                bookerId = rs.getString("booker_id"),
                                bookerName = rs.getString("booker_name"),
                                ownerName = rs.getString("owner_name"),
                                mobileNumber = rs.getString("mobile_number"),
                                address = rs.getString("address"),
                                city = rs.getString("city"),
                                openingCreditPaisa = rs.getLong("opening_credit_paisa"),
                                openingDateMillis = rs.getLong("opening_date_millis"),
                                notes = rs.getString("notes"),
                                isArchived = rs.getInt("is_archived") == 1,
                                createdByAccountId = rs.getString("created_by_account_id"),
                                createdByName = rs.getString("created_by_name")
                            )
                        )
                    }
                }
            }
        }
    }

    fun upsertProduct(p: ProductItem) = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT INTO products(id,business_id,sku,name,unit,unit_price_paisa,stock_qty,reorder_level,is_archived,updated_at_millis,sync_status)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              sku=excluded.sku,name=excluded.name,unit=excluded.unit,unit_price_paisa=excluded.unit_price_paisa,
              stock_qty=excluded.stock_qty,reorder_level=excluded.reorder_level,is_archived=excluded.is_archived,
              updated_at_millis=excluded.updated_at_millis,sync_status=excluded.sync_status
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, p.id); ps.setString(2, p.businessId); ps.setString(3, p.sku)
            ps.setString(4, p.name); ps.setString(5, p.unit); ps.setLong(6, p.unitPricePaisa)
            ps.setInt(7, p.stockQty); ps.setInt(8, p.reorderLevel)
            ps.setInt(9, if (p.isArchived) 1 else 0); ps.setLong(10, p.updatedAtMillis)
            ps.setString(11, p.syncStatus)
            ps.executeUpdate()
        }
    }

    fun replaceSyncedProducts(businessId: String, items: List<ProductItem>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM products WHERE business_id=? AND sync_status='SYNCED'").use {
                it.setString(1, businessId); it.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO products(id,business_id,sku,name,unit,unit_price_paisa,stock_qty,reorder_level,is_archived,updated_at_millis,sync_status) VALUES(?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(id) DO UPDATE SET sku=excluded.sku,name=excluded.name,unit=excluded.unit,unit_price_paisa=excluded.unit_price_paisa,stock_qty=excluded.stock_qty,reorder_level=excluded.reorder_level,is_archived=excluded.is_archived,updated_at_millis=excluded.updated_at_millis,sync_status=excluded.sync_status"
            ).use { ps ->
                items.forEach { p ->
                    ps.setString(1, p.id); ps.setString(2, p.businessId.ifBlank { businessId })
                    ps.setString(3, p.sku); ps.setString(4, p.name); ps.setString(5, p.unit)
                    ps.setLong(6, p.unitPricePaisa); ps.setInt(7, p.stockQty); ps.setInt(8, p.reorderLevel)
                    ps.setInt(9, if (p.isArchived) 1 else 0); ps.setLong(10, p.updatedAtMillis)
                    ps.setString(11, SyncStatus.SYNCED.key)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getProducts(businessId: String): List<ProductItem> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM products WHERE business_id=? ORDER BY name").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ProductItem(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                sku = rs.getString("sku"),
                                name = rs.getString("name"),
                                unit = rs.getString("unit"),
                                unitPricePaisa = rs.getLong("unit_price_paisa"),
                                stockQty = rs.getInt("stock_qty"),
                                reorderLevel = rs.getInt("reorder_level"),
                                isArchived = rs.getInt("is_archived") == 1,
                                updatedAtMillis = rs.getLong("updated_at_millis"),
                                syncStatus = rs.getString("sync_status")
                            )
                        )
                    }
                }
            }
        }
    }

    fun upsertBooking(bk: BookingOrder) = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT INTO bookings(id,business_id,booking_code,shop_id,shop_uid,shop_name,booker_id,booker_name,product_id,product_name,quantity,unit_price_paisa,total_amount_paisa,status,notes,booking_date_millis,booking_date_formatted,created_by_account_id,created_by_name,sync_status,idempotency_token,device_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              status=excluded.status,notes=excluded.notes,sync_status=excluded.sync_status
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, bk.id); ps.setString(2, bk.businessId); ps.setString(3, bk.bookingCode)
            ps.setString(4, bk.shopId); ps.setString(5, bk.shopUid); ps.setString(6, bk.shopName)
            ps.setString(7, bk.bookerId); ps.setString(8, bk.bookerName); ps.setString(9, bk.productId)
            ps.setString(10, bk.productName); ps.setInt(11, bk.quantity); ps.setLong(12, bk.unitPricePaisa)
            ps.setLong(13, bk.totalAmountPaisa); ps.setString(14, bk.status); ps.setString(15, bk.notes)
            ps.setLong(16, bk.bookingDateMillis); ps.setString(17, bk.bookingDateFormatted)
            ps.setString(18, bk.createdByAccountId); ps.setString(19, bk.createdByName)
            ps.setString(20, bk.syncStatus); ps.setString(21, bk.idempotencyToken); ps.setString(22, bk.deviceId)
            ps.executeUpdate()
        }
    }

    fun replaceSyncedBookings(businessId: String, items: List<BookingOrder>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM bookings WHERE business_id=? AND sync_status='SYNCED'").use {
                it.setString(1, businessId); it.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO bookings(id,business_id,booking_code,shop_id,shop_uid,shop_name,booker_id,booker_name,product_id,product_name,quantity,unit_price_paisa,total_amount_paisa,status,notes,booking_date_millis,booking_date_formatted,created_by_account_id,created_by_name,sync_status,idempotency_token,device_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(id) DO UPDATE SET status=excluded.status,sync_status='SYNCED'"
            ).use { ps ->
                items.forEach { bk ->
                    ps.setString(1, bk.id); ps.setString(2, bk.businessId.ifBlank { businessId })
                    ps.setString(3, bk.bookingCode); ps.setString(4, bk.shopId); ps.setString(5, bk.shopUid)
                    ps.setString(6, bk.shopName); ps.setString(7, bk.bookerId); ps.setString(8, bk.bookerName)
                    ps.setString(9, bk.productId); ps.setString(10, bk.productName); ps.setInt(11, bk.quantity)
                    ps.setLong(12, bk.unitPricePaisa); ps.setLong(13, bk.totalAmountPaisa); ps.setString(14, bk.status)
                    ps.setString(15, bk.notes); ps.setLong(16, bk.bookingDateMillis)
                    ps.setString(17, bk.bookingDateFormatted); ps.setString(18, bk.createdByAccountId)
                    ps.setString(19, bk.createdByName); ps.setString(20, SyncStatus.SYNCED.key)
                    ps.setString(21, bk.idempotencyToken); ps.setString(22, bk.deviceId)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getBookings(businessId: String, bookerFilterId: String? = null): List<BookingOrder> = withConnection { conn ->
        val sql = if (bookerFilterId.isNullOrBlank()) {
            "SELECT * FROM bookings WHERE business_id=? ORDER BY booking_date_millis DESC"
        } else {
            "SELECT * FROM bookings WHERE business_id=? AND booker_id=? ORDER BY booking_date_millis DESC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, businessId)
            if (!bookerFilterId.isNullOrBlank()) ps.setString(2, bookerFilterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            BookingOrder(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                bookingCode = rs.getString("booking_code"),
                                shopId = rs.getString("shop_id"),
                                shopUid = rs.getString("shop_uid"),
                                shopName = rs.getString("shop_name"),
                                bookerId = rs.getString("booker_id"),
                                bookerName = rs.getString("booker_name"),
                                productId = rs.getString("product_id"),
                                productName = rs.getString("product_name"),
                                quantity = rs.getInt("quantity"),
                                unitPricePaisa = rs.getLong("unit_price_paisa"),
                                totalAmountPaisa = rs.getLong("total_amount_paisa"),
                                status = rs.getString("status"),
                                notes = rs.getString("notes"),
                                bookingDateMillis = rs.getLong("booking_date_millis"),
                                bookingDateFormatted = rs.getString("booking_date_formatted"),
                                createdByAccountId = rs.getString("created_by_account_id"),
                                createdByName = rs.getString("created_by_name"),
                                syncStatus = rs.getString("sync_status"),
                                idempotencyToken = rs.getString("idempotency_token"),
                                deviceId = rs.getString("device_id")
                            )
                        )
                    }
                }
            }
        }
    }

    fun upsertCredit(c: CreditTransaction) = withConnection { conn ->
        conn.prepareStatement(creditInsertSql()).use { ps ->
            bindCredit(ps, c)
            ps.executeUpdate()
        }
    }

    fun replaceSyncedCredits(businessId: String, items: List<CreditTransaction>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM credits WHERE business_id=? AND sync_status='SYNCED'").use {
                it.setString(1, businessId); it.executeUpdate()
            }
            conn.prepareStatement(creditInsertSql()).use { ps ->
                items.forEach { c -> bindCredit(ps, c.copy(businessId = c.businessId.ifBlank { businessId }, syncStatus = SyncStatus.SYNCED.key)); ps.addBatch() }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    private fun creditInsertSql(): String =
        """
        INSERT INTO credits(id,business_id,tx_code,shop_id,shop_uid,shop_name,booker_id,booker_name,amount_paisa,transaction_kind,related_tx_id,reason,invoice_number,description,attachment_uri,tx_date_millis,tx_date_formatted,tx_time_formatted,created_by_account_id,created_by_name,created_by_role,due_date_millis,due_date_formatted,entry_timestamp_millis,entry_date_formatted,entry_time_formatted,sync_status,idempotency_token,device_id)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT(id) DO UPDATE SET sync_status=excluded.sync_status,amount_paisa=excluded.amount_paisa,description=excluded.description
        """.trimIndent()

    private fun bindCredit(ps: PreparedStatement, c: CreditTransaction) {
        ps.setString(1, c.id); ps.setString(2, c.businessId); ps.setString(3, c.txCode)
        ps.setString(4, c.shopId); ps.setString(5, c.shopUid); ps.setString(6, c.shopName)
        ps.setString(7, c.bookerId); ps.setString(8, c.bookerName); ps.setLong(9, c.amountPaisa)
        ps.setString(10, c.transactionKind); ps.setString(11, c.relatedTxId); ps.setString(12, c.reason)
        ps.setString(13, c.invoiceNumber); ps.setString(14, c.description); ps.setString(15, c.attachmentUri)
        ps.setLong(16, c.txDateMillis); ps.setString(17, c.txDateFormatted); ps.setString(18, c.txTimeFormatted)
        ps.setString(19, c.createdByAccountId); ps.setString(20, c.createdByName); ps.setString(21, c.createdByRole)
        ps.setLong(22, c.dueDateMillis); ps.setString(23, c.dueDateFormatted); ps.setLong(24, c.entryTimestampMillis)
        ps.setString(25, c.entryDateFormatted); ps.setString(26, c.entryTimeFormatted)
        ps.setString(27, c.syncStatus); ps.setString(28, c.idempotencyToken); ps.setString(29, c.deviceId)
    }

    fun getCredits(businessId: String, bookerFilterId: String? = null): List<CreditTransaction> = withConnection { conn ->
        val sql = if (bookerFilterId.isNullOrBlank()) {
            "SELECT * FROM credits WHERE business_id=? ORDER BY tx_date_millis DESC"
        } else {
            "SELECT * FROM credits WHERE business_id=? AND booker_id=? ORDER BY tx_date_millis DESC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, businessId)
            if (!bookerFilterId.isNullOrBlank()) ps.setString(2, bookerFilterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            CreditTransaction(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                txCode = rs.getString("tx_code"),
                                shopId = rs.getString("shop_id"),
                                shopUid = rs.getString("shop_uid"),
                                shopName = rs.getString("shop_name"),
                                bookerId = rs.getString("booker_id"),
                                bookerName = rs.getString("booker_name"),
                                amountPaisa = rs.getLong("amount_paisa"),
                                transactionKind = rs.getString("transaction_kind"),
                                relatedTxId = rs.getString("related_tx_id"),
                                reason = rs.getString("reason"),
                                invoiceNumber = rs.getString("invoice_number"),
                                description = rs.getString("description"),
                                attachmentUri = rs.getString("attachment_uri"),
                                txDateMillis = rs.getLong("tx_date_millis"),
                                txDateFormatted = rs.getString("tx_date_formatted"),
                                txTimeFormatted = rs.getString("tx_time_formatted"),
                                createdByAccountId = rs.getString("created_by_account_id"),
                                createdByName = rs.getString("created_by_name"),
                                createdByRole = rs.getString("created_by_role"),
                                dueDateMillis = rs.getLong("due_date_millis"),
                                dueDateFormatted = rs.getString("due_date_formatted"),
                                entryTimestampMillis = rs.getLong("entry_timestamp_millis"),
                                entryDateFormatted = rs.getString("entry_date_formatted"),
                                entryTimeFormatted = rs.getString("entry_time_formatted"),
                                syncStatus = rs.getString("sync_status"),
                                idempotencyToken = rs.getString("idempotency_token"),
                                deviceId = rs.getString("device_id")
                            )
                        )
                    }
                }
            }
        }
    }

    fun upsertRecovery(r: RecoveryTransaction) = withConnection { conn ->
        conn.prepareStatement(recoveryInsertSql()).use { ps ->
            bindRecovery(ps, r)
            ps.executeUpdate()
        }
    }

    fun replaceSyncedRecoveries(businessId: String, items: List<RecoveryTransaction>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM recoveries WHERE business_id=? AND sync_status='SYNCED'").use {
                it.setString(1, businessId); it.executeUpdate()
            }
            conn.prepareStatement(recoveryInsertSql()).use { ps ->
                items.forEach { r -> bindRecovery(ps, r.copy(businessId = r.businessId.ifBlank { businessId }, syncStatus = SyncStatus.SYNCED.key)); ps.addBatch() }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    private fun recoveryInsertSql(): String =
        """
        INSERT INTO recoveries(id,business_id,tx_code,shop_id,shop_uid,shop_name,booker_id,booker_name,amount_paisa,transaction_kind,related_tx_id,reason,payment_method,reference_number,notes,tx_date_millis,tx_date_formatted,tx_time_formatted,created_by_account_id,created_by_name,created_by_role,correction_group_id,corrected_from_tx_id,original_amount_paisa,original_tx_date_millis,is_backdated,reversed_at_millis,reversed_by_account_id,reversed_by_name,sync_status,idempotency_token,device_id)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT(id) DO UPDATE SET sync_status=excluded.sync_status,amount_paisa=excluded.amount_paisa,notes=excluded.notes
        """.trimIndent()

    private fun bindRecovery(ps: PreparedStatement, r: RecoveryTransaction) {
        ps.setString(1, r.id); ps.setString(2, r.businessId); ps.setString(3, r.txCode)
        ps.setString(4, r.shopId); ps.setString(5, r.shopUid); ps.setString(6, r.shopName)
        ps.setString(7, r.bookerId); ps.setString(8, r.bookerName); ps.setLong(9, r.amountPaisa)
        ps.setString(10, r.transactionKind); ps.setString(11, r.relatedTxId); ps.setString(12, r.reason)
        ps.setString(13, r.paymentMethod); ps.setString(14, r.referenceNumber); ps.setString(15, r.notes)
        ps.setLong(16, r.txDateMillis); ps.setString(17, r.txDateFormatted); ps.setString(18, r.txTimeFormatted)
        ps.setString(19, r.createdByAccountId); ps.setString(20, r.createdByName); ps.setString(21, r.createdByRole)
        ps.setString(22, r.correctionGroupId); ps.setString(23, r.correctedFromTxId)
        ps.setLong(24, r.originalAmountPaisa); ps.setLong(25, r.originalTxDateMillis)
        ps.setInt(26, if (r.isBackdated) 1 else 0); ps.setLong(27, r.reversedAtMillis)
        ps.setString(28, r.reversedByAccountId); ps.setString(29, r.reversedByName)
        ps.setString(30, r.syncStatus); ps.setString(31, r.idempotencyToken); ps.setString(32, r.deviceId)
    }

    fun getRecoveries(businessId: String, bookerFilterId: String? = null): List<RecoveryTransaction> = withConnection { conn ->
        val sql = if (bookerFilterId.isNullOrBlank()) {
            "SELECT * FROM recoveries WHERE business_id=? ORDER BY tx_date_millis DESC"
        } else {
            "SELECT * FROM recoveries WHERE business_id=? AND booker_id=? ORDER BY tx_date_millis DESC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, businessId)
            if (!bookerFilterId.isNullOrBlank()) ps.setString(2, bookerFilterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecoveryTransaction(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                txCode = rs.getString("tx_code"),
                                shopId = rs.getString("shop_id"),
                                shopUid = rs.getString("shop_uid"),
                                shopName = rs.getString("shop_name"),
                                bookerId = rs.getString("booker_id"),
                                bookerName = rs.getString("booker_name"),
                                amountPaisa = rs.getLong("amount_paisa"),
                                transactionKind = rs.getString("transaction_kind"),
                                relatedTxId = rs.getString("related_tx_id"),
                                reason = rs.getString("reason"),
                                paymentMethod = rs.getString("payment_method"),
                                referenceNumber = rs.getString("reference_number"),
                                notes = rs.getString("notes"),
                                txDateMillis = rs.getLong("tx_date_millis"),
                                txDateFormatted = rs.getString("tx_date_formatted"),
                                txTimeFormatted = rs.getString("tx_time_formatted"),
                                createdByAccountId = rs.getString("created_by_account_id"),
                                createdByName = rs.getString("created_by_name"),
                                createdByRole = rs.getString("created_by_role"),
                                correctionGroupId = rs.getString("correction_group_id"),
                                correctedFromTxId = rs.getString("corrected_from_tx_id"),
                                originalAmountPaisa = rs.getLong("original_amount_paisa"),
                                originalTxDateMillis = rs.getLong("original_tx_date_millis"),
                                isBackdated = rs.getInt("is_backdated") == 1,
                                reversedAtMillis = rs.getLong("reversed_at_millis"),
                                reversedByAccountId = rs.getString("reversed_by_account_id"),
                                reversedByName = rs.getString("reversed_by_name"),
                                syncStatus = rs.getString("sync_status"),
                                idempotencyToken = rs.getString("idempotency_token"),
                                deviceId = rs.getString("device_id")
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceAuditLogs(businessId: String, items: List<AuditLog>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM audit_logs WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO audit_logs(id,business_id,activity_code,user_id,username,role,action,shop_uid,previous_value,new_value,date_formatted,time_formatted,timestamp_millis,description) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { a ->
                    ps.setString(1, a.id); ps.setString(2, a.businessId.ifBlank { businessId })
                    ps.setString(3, a.activityCode); ps.setString(4, a.userId); ps.setString(5, a.username)
                    ps.setString(6, a.role); ps.setString(7, a.action); ps.setString(8, a.shopUid)
                    ps.setString(9, a.previousValue); ps.setString(10, a.newValue); ps.setString(11, a.dateFormatted)
                    ps.setString(12, a.timeFormatted); ps.setLong(13, a.timestampMillis); ps.setString(14, a.description)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getAuditLogs(businessId: String): List<AuditLog> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM audit_logs WHERE business_id=? ORDER BY timestamp_millis DESC LIMIT 200").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AuditLog(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                activityCode = rs.getString("activity_code"),
                                userId = rs.getString("user_id"),
                                username = rs.getString("username"),
                                role = rs.getString("role"),
                                action = rs.getString("action"),
                                shopUid = rs.getString("shop_uid"),
                                previousValue = rs.getString("previous_value"),
                                newValue = rs.getString("new_value"),
                                dateFormatted = rs.getString("date_formatted"),
                                timeFormatted = rs.getString("time_formatted"),
                                timestampMillis = rs.getLong("timestamp_millis"),
                                description = rs.getString("description")
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceNotifications(businessId: String, items: List<NotificationItem>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM notifications WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO notifications(id,business_id,title,message,category,target_role,target_booker_id,shop_uid,is_read,created_at_millis) VALUES(?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { n ->
                    ps.setString(1, n.id); ps.setString(2, n.businessId.ifBlank { businessId })
                    ps.setString(3, n.title); ps.setString(4, n.message); ps.setString(5, n.category)
                    ps.setString(6, n.targetRole); ps.setString(7, n.targetBookerId); ps.setString(8, n.shopUid)
                    ps.setInt(9, if (n.isRead) 1 else 0); ps.setLong(10, n.createdAtMillis)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getNotifications(businessId: String): List<NotificationItem> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM notifications WHERE business_id=? ORDER BY created_at_millis DESC").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            NotificationItem(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                title = rs.getString("title"),
                                message = rs.getString("message"),
                                category = rs.getString("category"),
                                targetRole = rs.getString("target_role"),
                                targetBookerId = rs.getString("target_booker_id"),
                                shopUid = rs.getString("shop_uid"),
                                isRead = rs.getInt("is_read") == 1,
                                createdAtMillis = rs.getLong("created_at_millis")
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceChatGroups(businessId: String, items: List<ChatGroup>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM chat_groups WHERE business_id=?").use { it.setString(1, businessId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO chat_groups(id,business_id,name,description,member_ids_csv,member_names_csv,created_by_account_id,created_by_name,last_message_preview,last_message_at_millis,is_archived) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { g ->
                    ps.setString(1, g.id); ps.setString(2, g.businessId.ifBlank { businessId })
                    ps.setString(3, g.name); ps.setString(4, g.description)
                    ps.setString(5, g.memberAccountIds.joinToString("\n"))
                    ps.setString(6, g.memberNames.joinToString("\n"))
                    ps.setString(7, g.createdByAccountId); ps.setString(8, g.createdByName)
                    ps.setString(9, g.lastMessagePreview); ps.setLong(10, g.lastMessageAtMillis)
                    ps.setInt(11, if (g.isArchived) 1 else 0)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getChatGroups(businessId: String): List<ChatGroup> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM chat_groups WHERE business_id=? ORDER BY last_message_at_millis DESC").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ChatGroup(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                name = rs.getString("name"),
                                description = rs.getString("description"),
                                memberAccountIds = rs.getString("member_ids_csv").split('\n').filter { it.isNotBlank() },
                                memberNames = rs.getString("member_names_csv").split('\n').filter { it.isNotBlank() },
                                createdByAccountId = rs.getString("created_by_account_id"),
                                createdByName = rs.getString("created_by_name"),
                                lastMessagePreview = rs.getString("last_message_preview"),
                                lastMessageAtMillis = rs.getLong("last_message_at_millis"),
                                isArchived = rs.getInt("is_archived") == 1
                            )
                        )
                    }
                }
            }
        }
    }

    fun replaceChatMessages(groupId: String, items: List<ChatMessage>) = withConnection { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM chat_messages WHERE group_id=?").use { it.setString(1, groupId); it.executeUpdate() }
            conn.prepareStatement(
                "INSERT INTO chat_messages(id,business_id,group_id,sender_account_id,sender_name,sender_role,message_type,text,file_name,mime_type,duration_sec,file_size_bytes,sent_at_millis,date_formatted,time_formatted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { m ->
                    ps.setString(1, m.id); ps.setString(2, m.businessId); ps.setString(3, m.groupId)
                    ps.setString(4, m.senderAccountId); ps.setString(5, m.senderName); ps.setString(6, m.senderRole)
                    ps.setString(7, m.messageType); ps.setString(8, m.text); ps.setString(9, m.fileName)
                    ps.setString(10, m.mimeType); ps.setInt(11, m.durationSec); ps.setLong(12, m.fileSizeBytes)
                    ps.setLong(13, m.sentAtMillis); ps.setString(14, m.dateFormatted); ps.setString(15, m.timeFormatted)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun getChatMessages(groupId: String): List<ChatMessage> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM chat_messages WHERE group_id=? ORDER BY sent_at_millis ASC").use { ps ->
            ps.setString(1, groupId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ChatMessage(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                groupId = rs.getString("group_id"),
                                senderAccountId = rs.getString("sender_account_id"),
                                senderName = rs.getString("sender_name"),
                                senderRole = rs.getString("sender_role"),
                                messageType = rs.getString("message_type"),
                                text = rs.getString("text"),
                                fileName = rs.getString("file_name"),
                                mimeType = rs.getString("mime_type"),
                                durationSec = rs.getInt("duration_sec"),
                                fileSizeBytes = rs.getLong("file_size_bytes"),
                                sentAtMillis = rs.getLong("sent_at_millis"),
                                dateFormatted = rs.getString("date_formatted"),
                                timeFormatted = rs.getString("time_formatted")
                            )
                        )
                    }
                }
            }
        }
    }

    // --- Offline Sync Queue (Duplicate-Safe + Conflict-Aware) ---

    fun insertPendingSyncIfAbsent(tx: PendingSyncTransaction): Boolean = withConnection { conn ->
        conn.prepareStatement(
            """
            INSERT OR IGNORE INTO pending_sync(
                local_id,business_id,user_id,username,user_role,device_id,shop_id,shop_uid,shop_name,booker_id,booker_name,
                tx_type,amount_paisa,original_tx_date_millis,due_date_millis,invoice_or_ref_number,payment_method,description_or_notes,
                local_device_timestamp,sync_status,idempotency_token,audit_metadata,error_message,retry_count,server_synced_at_millis
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, tx.localId); ps.setString(2, tx.businessId); ps.setString(3, tx.userId)
            ps.setString(4, tx.username); ps.setString(5, tx.userRole); ps.setString(6, tx.deviceId)
            ps.setString(7, tx.shopId); ps.setString(8, tx.shopUid); ps.setString(9, tx.shopName)
            ps.setString(10, tx.bookerId); ps.setString(11, tx.bookerName); ps.setString(12, tx.txType)
            ps.setLong(13, tx.amountPaisa); ps.setLong(14, tx.originalTxDateMillis)
            ps.setLong(15, tx.dueDateMillis); ps.setString(16, tx.invoiceOrRefNumber)
            ps.setString(17, tx.paymentMethod); ps.setString(18, tx.descriptionOrNotes)
            ps.setLong(19, tx.localDeviceTimestamp); ps.setString(20, tx.syncStatus)
            ps.setString(21, tx.idempotencyToken); ps.setString(22, tx.auditMetadata)
            ps.setString(23, tx.errorMessage); ps.setInt(24, tx.retryCount)
            ps.setLong(25, tx.serverSyncedAtMillis)
            ps.executeUpdate() > 0
        }
    }

    fun getPendingSyncItems(businessId: String? = null): List<PendingSyncTransaction> = withConnection { conn ->
        val sql = if (businessId.isNullOrBlank()) {
            "SELECT * FROM pending_sync ORDER BY local_device_timestamp ASC"
        } else {
            "SELECT * FROM pending_sync WHERE business_id=? ORDER BY local_device_timestamp ASC"
        }
        conn.prepareStatement(sql).use { ps ->
            if (!businessId.isNullOrBlank()) ps.setString(1, businessId)
            ps.executeQuery().use { rs -> readPendingList(rs) }
        }
    }

    private fun readPendingList(rs: ResultSet): List<PendingSyncTransaction> = buildList {
        while (rs.next()) {
            add(
                PendingSyncTransaction(
                    localId = rs.getString("local_id"),
                    businessId = rs.getString("business_id"),
                    userId = rs.getString("user_id"),
                    username = rs.getString("username"),
                    userRole = rs.getString("user_role"),
                    deviceId = rs.getString("device_id"),
                    shopId = rs.getString("shop_id"),
                    shopUid = rs.getString("shop_uid"),
                    shopName = rs.getString("shop_name"),
                    bookerId = rs.getString("booker_id"),
                    bookerName = rs.getString("booker_name"),
                    txType = rs.getString("tx_type"),
                    amountPaisa = rs.getLong("amount_paisa"),
                    originalTxDateMillis = rs.getLong("original_tx_date_millis"),
                    dueDateMillis = rs.getLong("due_date_millis"),
                    invoiceOrRefNumber = rs.getString("invoice_or_ref_number"),
                    paymentMethod = rs.getString("payment_method"),
                    descriptionOrNotes = rs.getString("description_or_notes"),
                    localDeviceTimestamp = rs.getLong("local_device_timestamp"),
                    syncStatus = rs.getString("sync_status"),
                    idempotencyToken = rs.getString("idempotency_token"),
                    auditMetadata = rs.getString("audit_metadata"),
                    errorMessage = rs.getString("error_message"),
                    retryCount = rs.getInt("retry_count"),
                    serverSyncedAtMillis = rs.getLong("server_synced_at_millis")
                )
            )
        }
    }

    fun updatePendingSyncState(
        localId: String,
        syncStatus: String,
        errorMessage: String = "",
        retryCount: Int? = null,
        serverSyncedAtMillis: Long = 0L
    ) = withConnection { conn ->
        if (retryCount != null) {
            conn.prepareStatement(
                "UPDATE pending_sync SET sync_status=?, error_message=?, retry_count=?, server_synced_at_millis=? WHERE local_id=?"
            ).use { ps ->
                ps.setString(1, syncStatus); ps.setString(2, errorMessage)
                ps.setInt(3, retryCount); ps.setLong(4, serverSyncedAtMillis); ps.setString(5, localId)
                ps.executeUpdate()
            }
        } else {
            conn.prepareStatement(
                "UPDATE pending_sync SET sync_status=?, error_message=?, server_synced_at_millis=? WHERE local_id=?"
            ).use { ps ->
                ps.setString(1, syncStatus); ps.setString(2, errorMessage)
                ps.setLong(3, serverSyncedAtMillis); ps.setString(4, localId)
                ps.executeUpdate()
            }
        }
    }

    fun deleteSyncedPendingItem(localId: String) = withConnection { conn ->
        conn.prepareStatement("DELETE FROM pending_sync WHERE local_id=? AND sync_status=?").use { ps ->
            ps.setString(1, localId)
            ps.setString(2, SyncStatus.SYNCED.key)
            ps.executeUpdate()
        }
    }

    fun markLocalTransactionSynced(txType: String, docId: String) = withConnection { conn ->
        val table = when (txType) {
            "CREDIT" -> "credits"
            "RECOVERY" -> "recoveries"
            "BOOKING" -> "bookings"
            "PRODUCT" -> "products"
            else -> null
        } ?: return@withConnection
        conn.prepareStatement("UPDATE $table SET sync_status='SYNCED' WHERE id=?").use { ps ->
            ps.setString(1, docId)
            ps.executeUpdate()
        }
    }

    fun insertSyncHistory(record: SyncHistoryRecord) = withConnection { conn ->
        conn.prepareStatement(
            "INSERT OR REPLACE INTO sync_history(id,business_id,local_tx_id,shop_uid,tx_type,amount_paisa,sync_status,message,synced_at_millis) VALUES(?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, record.id); ps.setString(2, record.businessId)
            ps.setString(3, record.localTxId); ps.setString(4, record.shopUid)
            ps.setString(5, record.txType); ps.setLong(6, record.amountPaisa)
            ps.setString(7, record.syncStatus); ps.setString(8, record.message)
            ps.setLong(9, record.syncedAtMillis)
            ps.executeUpdate()
        }
    }

    fun getSyncHistory(businessId: String): List<SyncHistoryRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM sync_history WHERE business_id=? ORDER BY synced_at_millis DESC LIMIT 100").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SyncHistoryRecord(
                                id = rs.getString("id"),
                                businessId = rs.getString("business_id"),
                                localTxId = rs.getString("local_tx_id"),
                                shopUid = rs.getString("shop_uid"),
                                txType = rs.getString("tx_type"),
                                amountPaisa = rs.getLong("amount_paisa"),
                                syncStatus = rs.getString("sync_status"),
                                message = rs.getString("message"),
                                syncedAtMillis = rs.getLong("synced_at_millis")
                            )
                        )
                    }
                }
            }
        }
    }

    fun checkIntegrity(): String = withConnection { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA integrity_check;").use { rs ->
                if (rs.next()) rs.getString(1) else "unknown"
            }
        }
    }

    fun countUnsynced(businessId: String): Int = withConnection { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM pending_sync WHERE business_id=? AND sync_status != 'SYNCED'").use { ps ->
            ps.setString(1, businessId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun backupSqliteDatabaseTo(targetFile: File) {
        synchronized(lock) {
            targetFile.parentFile?.mkdirs()
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE);") }
            }
            dbFile.copyTo(targetFile, overwrite = true)
        }
    }

    fun restoreSqliteDatabaseFrom(sourceFile: File): Result<Unit> = runCatching {
        require(sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 100L) {
            "Selected backup file is empty or unreadable."
        }
        val integrity = DriverManager.getConnection("jdbc:sqlite:${sourceFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA integrity_check;").use { rs ->
                    if (rs.next()) rs.getString(1) else "invalid"
                }
            }
        }
        require(integrity.equals("ok", ignoreCase = true)) {
            "Backup database failed integrity verification ($integrity)."
        }
        synchronized(lock) {
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE);") }
            }
            File("${dbFile.absolutePath}-wal").delete()
            File("${dbFile.absolutePath}-shm").delete()
            sourceFile.copyTo(dbFile, overwrite = true)
            initializeSchema()
        }
    }

    fun discardConflictedLocalItem(txType: String, localId: String) = withConnection { conn ->
        conn.autoCommit = false
        try {
            val table = when (txType) {
                "CREDIT" -> "credits"
                "RECOVERY" -> "recoveries"
                "BOOKING" -> "bookings"
                else -> null
            }
            if (table != null) {
                conn.prepareStatement("DELETE FROM $table WHERE id=? AND sync_status != 'SYNCED'").use { ps ->
                    ps.setString(1, localId)
                    ps.executeUpdate()
                }
            }
            conn.prepareStatement("DELETE FROM pending_sync WHERE local_id=?").use { ps ->
                ps.setString(1, localId)
                ps.executeUpdate()
            }
            conn.commit()
        } finally {
            conn.autoCommit = true
        }
    }

    fun clearBusinessCacheOnlyIfSafe(businessId: String): Result<Unit> = runCatching {
        val unsynced = countUnsynced(businessId)
        require(unsynced == 0) {
            "Cannot clear local cache while $unsynced pending/unsynced financial transactions remain. Sync first."
        }
        withConnection { conn ->
            conn.autoCommit = false
            try {
                listOf("staff_users", "permissions", "bookers", "shops", "products", "bookings", "credits", "recoveries", "audit_logs", "notifications", "chat_groups", "chat_messages", "sync_history").forEach { table ->
                    conn.prepareStatement("DELETE FROM $table WHERE business_id=?").use {
                        it.setString(1, businessId); it.executeUpdate()
                    }
                }
                conn.commit()
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun getDatabasePath(): String = dbFile.absolutePath
}
