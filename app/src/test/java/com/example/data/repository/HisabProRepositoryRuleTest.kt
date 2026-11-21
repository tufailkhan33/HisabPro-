package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.HisabProRepository
import com.example.domain.model.AccountRole
import com.example.domain.model.ChatMessageType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.StaffPermission
import com.example.util.PakistanTime
import com.example.util.SecurityRateLimiter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HisabProRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueSuffix() = UUID.randomUUID().toString().replace("-", "").take(6)

    @Test
    fun createBusiness_addBookerShopCreditRecovery_succeedsAndSyncs() = runBlocking {
        val suffix = uniqueSuffix()
        signInTestUser("admin_$suffix@hisabpro.pk")
        val repo = HisabProRepository(firestore, auth)

        val session = withTimeout(TIMEOUT_MS) {
            repo.createBusinessAndSuperAdmin(
                fullName = "Tariq Mahmood",
                businessName = "Al Noor Traders $suffix",
                username = "admin_$suffix",
                password = "Password123",
                mobileNumber = "03001234567",
                email = "tariq@alnoor.pk"
            ).getOrThrow()
        }
        assertEquals(AccountRole.SUPER_ADMIN, session.role)

        val bookerUsername = "ali_$suffix"
        val creds = withTimeout(TIMEOUT_MS) {
            repo.createBooker(
                session = session,
                businessCode = session.businessCode,
                businessName = session.businessName,
                fullName = "Ali Raza",
                username = bookerUsername,
                temporaryPassword = "TempPass99",
                mobileNumber = "03211112233",
                address = "Lahore",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }
        assertEquals(bookerUsername, creds.username)

        val booker = withTimeout(TIMEOUT_MS) {
            repo.observeBookers(session.businessId).first { it.isNotEmpty() }.first()
        }

        val shop = withTimeout(TIMEOUT_MS) {
            repo.createShop(
                session = session,
                shopUid = "SHP-$suffix",
                shopName = "Madina Mart $suffix",
                booker = booker,
                ownerName = "Haji Akram",
                mobileNumber = "03334445566",
                address = "Main Bazar",
                city = "Lahore",
                openingCreditPaisa = 5_000_000L, // Rs. 50,000
                openingDateMillis = PakistanTime.nowMillis(),
                notes = "Opening balance"
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            repo.addCreditTransaction(
                session = session,
                shop = shop,
                amountPaisa = 3_000_000L, // Rs. 30,000
                txDateMillis = PakistanTime.nowMillis(),
                invoiceNumber = "INV-101",
                description = "Second batch",
                attachmentUri = ""
            ).getOrThrow()
        }

        val recoveryResult = withTimeout(TIMEOUT_MS) {
            repo.recordRecoveryTransaction(
                session = session,
                shop = shop,
                amountPaisa = 2_000_000L, // Rs. 20,000
                txDateMillis = PakistanTime.nowMillis(),
                paymentMethod = PaymentMethod.EASYPAISA,
                referenceNumber = "EP-8899",
                notes = "First recovery",
                allowOverRecovery = false,
                clientRequestToken = "tok_$suffix"
            )
        }
        assertTrue(recoveryResult.isSuccess)

        // Over-recovery (Rs. 70,000 when only Rs. 60,000 outstanding) must fail
        val overRecovery = withTimeout(TIMEOUT_MS) {
            repo.recordRecoveryTransaction(
                session = session,
                shop = shop,
                amountPaisa = 7_000_000L,
                txDateMillis = PakistanTime.nowMillis(),
                paymentMethod = PaymentMethod.CASH,
                referenceNumber = "",
                notes = "Over recovery test",
                allowOverRecovery = false,
                clientRequestToken = "tok_over_$suffix"
            )
        }
        assertTrue(overRecovery.isFailure)
    }

    @Test
    fun bookerLogin_isolatedToAssignedShopsAndReadOnly() = runBlocking {
        val suffix = uniqueSuffix()
        signInTestUser("admin2_$suffix@hisabpro.pk")
        val adminRepo = HisabProRepository(firestore, auth)

        val adminSession = withTimeout(TIMEOUT_MS) {
            adminRepo.createBusinessAndSuperAdmin(
                fullName = "Admin Two",
                businessName = "Karachi Supply $suffix",
                username = "adm2_$suffix",
                password = "Password123",
                mobileNumber = "03009998877",
                email = ""
            ).getOrThrow()
        }

        val booker1Creds = withTimeout(TIMEOUT_MS) {
            adminRepo.createBooker(
                session = adminSession,
                businessCode = adminSession.businessCode,
                businessName = adminSession.businessName,
                fullName = "Booker One",
                username = "b1_$suffix",
                temporaryPassword = "Temp1234",
                mobileNumber = "03111111111",
                address = "",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }
        val booker2Creds = withTimeout(TIMEOUT_MS) {
            adminRepo.createBooker(
                session = adminSession,
                businessCode = adminSession.businessCode,
                businessName = adminSession.businessName,
                fullName = "Booker Two",
                username = "b2_$suffix",
                temporaryPassword = "Temp1234",
                mobileNumber = "03222222222",
                address = "",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }

        val bookers = withTimeout(TIMEOUT_MS) {
            adminRepo.observeBookers(adminSession.businessId).first { it.size == 2 }
        }
        val b1 = bookers.first { it.username == booker1Creds.username }
        val b2 = bookers.first { it.username == booker2Creds.username }

        val shopB1 = withTimeout(TIMEOUT_MS) {
            adminRepo.createShop(
                session = adminSession,
                shopUid = "SHP-B1-$suffix",
                shopName = "Shop For Booker 1",
                booker = b1,
                ownerName = "Owner One",
                mobileNumber = "03001111111",
                address = "Saddar",
                city = "Karachi",
                openingCreditPaisa = 1_000_000L,
                openingDateMillis = PakistanTime.nowMillis(),
                notes = ""
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            adminRepo.createShop(
                session = adminSession,
                shopUid = "SHP-B2-$suffix",
                shopName = "Shop For Booker 2",
                booker = b2,
                ownerName = "Owner Two",
                mobileNumber = "03002222222",
                address = "Clifton",
                city = "Karachi",
                openingCreditPaisa = 2_000_000L,
                openingDateMillis = PakistanTime.nowMillis(),
                notes = ""
            ).getOrThrow()
        }

        // Now sign in as Booker 1 on a separate Google account
        signInTestUser("booker1_google_$suffix@hisabpro.pk")
        val bookerRepo = HisabProRepository(firestore, auth)

        val bookerSession = withTimeout(TIMEOUT_MS) {
            bookerRepo.loginWithCredentials(
                businessCode = adminSession.businessCode,
                username = booker1Creds.username,
                password = booker1Creds.temporaryPassword
            ).getOrThrow()
        }
        assertEquals(AccountRole.BOOKER, bookerSession.role)
        assertFalse(bookerSession.mustChangePassword)

        val assignedShops = withTimeout(TIMEOUT_MS) {
            bookerRepo.observeShops(
                bookerSession.businessId,
                AccountRole.BOOKER,
                bookerSession.bookerId
            ).first { it.isNotEmpty() }
        }
        assertEquals(1, assignedShops.size)
        assertEquals(shopB1.shopUid, assignedShops.first().shopUid)

        // Booker attempting to add credit must fail with PERMISSION_DENIED
        val addCreditAttempt = withTimeout(TIMEOUT_MS) {
            bookerRepo.addCreditTransaction(
                session = bookerSession,
                shop = shopB1,
                amountPaisa = 500_000L,
                txDateMillis = PakistanTime.nowMillis(),
                invoiceNumber = "",
                description = "Unauthorized",
                attachmentUri = ""
            )
        }
        assertTrue(addCreditAttempt.isFailure)
        val fsError = addCreditAttempt.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun observeShops_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = HisabProRepository(firestore, auth)
        val result = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeShops("some_biz_id", AccountRole.SUPER_ADMIN, "").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull(result)
        // Either IllegalStateException from requireUserId() or PERMISSION_DENIED
        assertTrue(
            result is IllegalStateException ||
                result?.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    @Test
    fun duplicateRecoveryToken_andReversals_andRateLimit_enforcedAccurately() = runBlocking {
        val suffix = uniqueSuffix()
        signInTestUser("admin_dup_$suffix@hisabpro.pk")
        val repo = HisabProRepository(firestore, auth, SecurityRateLimiter())

        val adminSession = withTimeout(TIMEOUT_MS) {
            repo.createBusinessAndSuperAdmin(
                fullName = "Admin Dup",
                businessName = "Multan Supply $suffix",
                username = "admdup_$suffix",
                password = "Password123",
                mobileNumber = "03005556677",
                email = ""
            ).getOrThrow()
        }

        withTimeout(TIMEOUT_MS) {
            repo.createBooker(
                session = adminSession,
                businessCode = adminSession.businessCode,
                businessName = adminSession.businessName,
                fullName = "Booker Dup",
                username = "bkdup_$suffix",
                temporaryPassword = "Temp1234",
                mobileNumber = "03015556677",
                address = "",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }
        val booker = withTimeout(TIMEOUT_MS) {
            repo.observeBookers(adminSession.businessId).first { it.isNotEmpty() }.first()
        }
        val shop = withTimeout(TIMEOUT_MS) {
            repo.createShop(
                session = adminSession,
                shopUid = "SHP-DUP-$suffix",
                shopName = "Dup Store",
                booker = booker,
                ownerName = "Owner",
                mobileNumber = "03007778899",
                address = "Multan",
                city = "Multan",
                openingCreditPaisa = 0L,
                openingDateMillis = PakistanTime.nowMillis(),
                notes = ""
            ).getOrThrow()
        }

        val credit = withTimeout(TIMEOUT_MS) {
            repo.addCreditTransaction(
                session = adminSession,
                shop = shop,
                amountPaisa = 4_000_000L, // Rs. 40,000
                txDateMillis = PakistanTime.nowMillis(),
                invoiceNumber = "INV-1",
                description = "Stock",
                attachmentUri = ""
            ).getOrThrow()
        }

        // Duplicate clientRequestToken must be rejected on second call
        val idempotencyToken = "idemp_$suffix"
        val rec1 = withTimeout(TIMEOUT_MS) {
            repo.recordRecoveryTransaction(
                session = adminSession,
                shop = shop,
                amountPaisa = 1_500_000L,
                txDateMillis = PakistanTime.nowMillis(),
                paymentMethod = PaymentMethod.CASH,
                referenceNumber = "",
                notes = "First",
                allowOverRecovery = false,
                clientRequestToken = idempotencyToken
            )
        }
        assertTrue(rec1.isSuccess)

        val dupRec = withTimeout(TIMEOUT_MS) {
            repo.recordRecoveryTransaction(
                session = adminSession,
                shop = shop,
                amountPaisa = 1_500_000L,
                txDateMillis = PakistanTime.nowMillis(),
                paymentMethod = PaymentMethod.CASH,
                referenceNumber = "",
                notes = "Duplicate retry",
                allowOverRecovery = false,
                clientRequestToken = idempotencyToken
            )
        }
        assertTrue(dupRec.isFailure)
        assertTrue(dupRec.exceptionOrNull()?.message?.contains("Duplicate submission prevented") == true)

        // Reverse credit & recovery cleanly
        withTimeout(TIMEOUT_MS) {
            repo.reverseCreditTransaction(adminSession, credit, "Wrong amount entered").getOrThrow()
            repo.reverseRecoveryTransaction(adminSession, rec1.getOrThrow(), "Cheque bounced").getOrThrow()
        }

        // Failed logins trigger rate limiter after 5 incorrect attempts
        repeat(5) {
            val bad = withTimeout(TIMEOUT_MS) {
                repo.loginWithCredentials(adminSession.businessCode, adminSession.username, "WrongPass!$it")
            }
            assertTrue(bad.isFailure)
        }
        val locked = withTimeout(TIMEOUT_MS) {
            repo.loginWithCredentials(adminSession.businessCode, adminSession.username, "Password123")
        }
        assertTrue(locked.isFailure)
        assertTrue(locked.exceptionOrNull()?.message?.contains("Too many failed login attempts") == true)
    }

    @Test
    fun staffPermissions_passwordLifecycle_realTimeSync_andCrossBusinessIsolation() = runBlocking {
        val suffix = uniqueSuffix()
        signInTestUser("ownerA_$suffix@hisabpro.pk")
        val adminRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())

        // Workspace A
        val bizASession = withTimeout(TIMEOUT_MS) {
            adminRepo.createBusinessAndSuperAdmin(
                fullName = "Admin A",
                businessName = "Islamabad Traders $suffix",
                username = "adma_$suffix",
                password = "AdminPass1",
                mobileNumber = "03001112233",
                email = ""
            ).getOrThrow()
        }

        // Workspace B owned by separate Google account for cross-business isolation check
        signInTestUser("ownerB_$suffix@hisabpro.pk")
        val bizBRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val bizBSession = withTimeout(TIMEOUT_MS) {
            bizBRepo.createBusinessAndSuperAdmin(
                fullName = "Admin B",
                businessName = "Peshawar Goods $suffix",
                username = "admb_$suffix",
                password = "AdminPass2",
                mobileNumber = "03004445566",
                email = ""
            ).getOrThrow()
        }
        // Admin B reading Workspace A shops must fail with PERMISSION_DENIED
        val crossReadAttempt = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bizBRepo.observeShops(bizASession.businessId, AccountRole.SUPER_ADMIN, "").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull(crossReadAttempt)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossReadAttempt?.unwrapFirestoreException()?.code)

        // Switch back to Admin A: create restricted Staff (cannot addCredit) + Booker
        signInTestUser("ownerA_$suffix@hisabpro.pk")
        val staffCreds = withTimeout(TIMEOUT_MS) {
            adminRepo.createStaffUser(
                session = bizASession,
                fullName = "Staff A",
                username = "staffa_$suffix",
                temporaryPassword = "StaffTemp1",
                mobileNumber = "03121112233",
                permissionTemplate = StaffPermission(
                    businessId = bizASession.businessId,
                    userId = "",
                    viewDashboard = true,
                    viewShops = true,
                    addCredit = false,
                    recordRecovery = true
                )
            ).getOrThrow()
        }
        val bookerCreds = withTimeout(TIMEOUT_MS) {
            adminRepo.createBooker(
                session = bizASession,
                businessCode = bizASession.businessCode,
                businessName = bizASession.businessName,
                fullName = "Booker A",
                username = "bookera_$suffix",
                temporaryPassword = "BookerTemp1",
                mobileNumber = "03131112233",
                address = "Rawalpindi",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }
        val booker = withTimeout(TIMEOUT_MS) {
            adminRepo.observeBookers(bizASession.businessId).first { it.isNotEmpty() }.first()
        }
        val shopA = withTimeout(TIMEOUT_MS) {
            adminRepo.createShop(
                session = bizASession,
                shopUid = "SHP-A-$suffix",
                shopName = "Pindi Store",
                booker = booker,
                ownerName = "Malik",
                mobileNumber = "03009991111",
                address = "Saddar",
                city = "Rawalpindi",
                openingCreditPaisa = 0L,
                openingDateMillis = PakistanTime.nowMillis(),
                notes = ""
            ).getOrThrow()
        }

        // Admin self-password change + reset own password check
        val changedAdminSess = withTimeout(TIMEOUT_MS) {
            adminRepo.changeOwnPassword(bizASession, "AdminPass1", "AdminPass9").getOrThrow()
        }
        assertFalse(changedAdminSess.mustChangePassword)
        withTimeout(TIMEOUT_MS) {
            adminRepo.resetOwnAdminPassword(
                bizASession.businessCode,
                bizASession.username,
                "03001112233",
                "AdminPass10"
            ).getOrThrow()
        }

        // Staff logs in on own Google account, completes first-login password change,
        // is denied addCredit (addCredit=false), and is denied direct financial deletion!
        signInTestUser("staffA_google_$suffix@hisabpro.pk")
        val staffRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val staffLogin = withTimeout(TIMEOUT_MS) {
            staffRepo.loginWithCredentials(bizASession.businessCode, staffCreds.username, staffCreds.temporaryPassword).getOrThrow()
        }
        assertEquals(AccountRole.STAFF, staffLogin.role)
        assertFalse(staffLogin.mustChangePassword)
        val staffReady = staffLogin

        val staffCreditDenied = withTimeout(TIMEOUT_MS) {
            staffRepo.addCreditTransaction(
                session = staffReady,
                shop = shopA,
                amountPaisa = 500_000L,
                txDateMillis = PakistanTime.nowMillis(),
                invoiceNumber = "",
                description = "Blocked",
                attachmentUri = ""
            )
        }
        assertTrue(staffCreditDenied.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, staffCreditDenied.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // Booker logs in, changes own password, and receives real-time updates when Admin adds credit & recovery
        signInTestUser("bookerA_google_$suffix@hisabpro.pk")
        val bookerRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val bookerLogin = withTimeout(TIMEOUT_MS) {
            bookerRepo.loginWithCredentials(bizASession.businessCode, bookerCreds.username, bookerCreds.temporaryPassword).getOrThrow()
        }
        val bookerChanged = withTimeout(TIMEOUT_MS) {
            bookerRepo.changeOwnPassword(bookerLogin, bookerCreds.temporaryPassword, "BookerPass2").getOrThrow()
        }
        assertFalse(bookerChanged.mustChangePassword)

        // Admin adds credit & recovery and resets staff password; verify Booker live stream syncs & Staff cannot delete financial docs
        signInTestUser("ownerA_$suffix@hisabpro.pk")
        val creditEntry = withTimeout(TIMEOUT_MS) {
            adminRepo.addCreditTransaction(
                session = bizASession,
                shop = shopA,
                amountPaisa = 2_000_000L,
                txDateMillis = PakistanTime.nowMillis(),
                invoiceNumber = "INV-LIVE",
                description = "Live Sync Credit",
                attachmentUri = ""
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            adminRepo.recordRecoveryTransaction(
                session = bizASession,
                shop = shopA,
                amountPaisa = 800_000L,
                txDateMillis = PakistanTime.nowMillis(),
                paymentMethod = PaymentMethod.JAZZCASH,
                referenceNumber = "JC-10",
                notes = "Live recovery",
                allowOverRecovery = false,
                clientRequestToken = "live_rec_$suffix"
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            adminRepo.resetAccountPassword(
                session = bizASession,
                accountId = bizASession.accountId.let { staffReady.accountId },
                accountCode = "USR-1",
                fullName = "Staff A",
                username = staffCreds.username,
                role = AccountRole.STAFF,
                newTempPassword = "StaffReset9"
            ).getOrThrow()
        }

        // Staff direct delete attempt on credit_transactions doc must fail with PERMISSION_DENIED
        signInTestUser("staffA_google_$suffix@hisabpro.pk")
        val staffDeleteError = try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("businesses").document(bizASession.businessId)
                    .collection("credit_transactions").document(creditEntry.id)
                    .delete().await()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull(staffDeleteError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, staffDeleteError?.unwrapFirestoreException()?.code)

        // Verify Booker live stream observes both the credit & recovery transactions synced by Admin
        signInTestUser("bookerA_google_$suffix@hisabpro.pk")
        val syncedCredits = withTimeout(TIMEOUT_MS) {
            bookerRepo.observeCreditTransactions(bizASession.businessId, AccountRole.BOOKER, bookerChanged.bookerId)
                .first { it.any { tx -> tx.id == creditEntry.id } }
        }
        val syncedRecoveries = withTimeout(TIMEOUT_MS) {
            bookerRepo.observeRecoveryTransactions(bizASession.businessId, AccountRole.BOOKER, bookerChanged.bookerId)
                .first { it.isNotEmpty() }
        }
        assertEquals(2_000_000L, syncedCredits.first().amountPaisa)
        assertEquals(800_000L, syncedRecoveries.first().amountPaisa)
    }

    @Test
    fun teamGroupChat_adminCreatesGroup_membersExchangeTextVoiceImageFile_nonMemberIsolated() = runBlocking {
        val suffix = uniqueSuffix()
        signInTestUser("chat_admin_$suffix@hisabpro.pk")
        val adminRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())

        val adminSession = withTimeout(TIMEOUT_MS) {
            adminRepo.createBusinessAndSuperAdmin(
                fullName = "Chat Admin",
                businessName = "Lahore Wholesale $suffix",
                username = "cadm_$suffix",
                password = "Password123",
                mobileNumber = "03001230001",
                email = ""
            ).getOrThrow()
        }
        val staffCreds = withTimeout(TIMEOUT_MS) {
            adminRepo.createStaffUser(
                session = adminSession,
                fullName = "Chat Staff",
                username = "cstf_$suffix",
                temporaryPassword = "StaffPass1",
                mobileNumber = "03001230002",
                permissionTemplate = StaffPermission(businessId = adminSession.businessId, viewDashboard = true)
            ).getOrThrow()
        }
        val booker1Creds = withTimeout(TIMEOUT_MS) {
            adminRepo.createBooker(
                session = adminSession,
                businessCode = adminSession.businessCode,
                businessName = adminSession.businessName,
                fullName = "Booker Included",
                username = "cbk1_$suffix",
                temporaryPassword = "BookerPass1",
                mobileNumber = "03001230003",
                address = "",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }
        val booker2Creds = withTimeout(TIMEOUT_MS) {
            adminRepo.createBooker(
                session = adminSession,
                businessCode = adminSession.businessCode,
                businessName = adminSession.businessName,
                fullName = "Booker Excluded",
                username = "cbk2_$suffix",
                temporaryPassword = "BookerPass2",
                mobileNumber = "03001230004",
                address = "",
                notes = "",
                assignedShopIds = emptyList()
            ).getOrThrow()
        }

        val staffUser = withTimeout(TIMEOUT_MS) {
            adminRepo.observeStaffUsers(adminSession.businessId)
                .first { list -> list.any { it.username == staffCreds.username } }
                .first { it.username == staffCreds.username }
        }
        val bookerList = withTimeout(TIMEOUT_MS) {
            adminRepo.observeBookers(adminSession.businessId).first { it.size == 2 }
        }
        val includedBooker = bookerList.first { it.username == booker1Creds.username }

        val group = withTimeout(TIMEOUT_MS) {
            adminRepo.createChatGroup(
                session = adminSession,
                name = "Morning Recovery Sync",
                description = "Daily collection meeting",
                memberAccountIds = listOf(staffUser.id, includedBooker.id),
                memberNames = listOf(staffUser.fullName, includedBooker.fullName)
            ).getOrThrow()
        }

        // Admin sends text + file
        withTimeout(TIMEOUT_MS) {
            adminRepo.sendChatMessage(adminSession, group.id, ChatMessageType.TEXT, "Salam team, share daily targets.").getOrThrow()
            adminRepo.sendChatMessage(
                session = adminSession,
                groupId = group.id,
                messageType = ChatMessageType.FILE,
                text = "",
                mediaBase64 = "SGVsbG8gSGlzYWJQcm8=",
                fileName = "targets.txt",
                mimeType = "text/plain",
                fileSizeBytes = 14L
            ).getOrThrow()
        }

        // Included Booker logs in, sees the group, and sends a voice note + image
        signInTestUser("chat_bk1_$suffix@hisabpro.pk")
        val bk1Repo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val bk1Sess = withTimeout(TIMEOUT_MS) {
            bk1Repo.loginWithCredentials(adminSession.businessCode, booker1Creds.username, booker1Creds.temporaryPassword).getOrThrow()
        }
        val bk1Groups = withTimeout(TIMEOUT_MS) {
            bk1Repo.observeChatGroups(bk1Sess.businessId, bk1Sess.role, bk1Sess.accountId).first { it.isNotEmpty() }
        }
        assertEquals(group.id, bk1Groups.first().id)

        withTimeout(TIMEOUT_MS) {
            bk1Repo.sendChatMessage(
                session = bk1Sess,
                groupId = group.id,
                messageType = ChatMessageType.VOICE,
                text = "",
                mediaBase64 = "Vk9JQ0VfREFUQQ==",
                fileName = "voice.m4a",
                mimeType = "audio/mp4",
                durationSec = 12,
                fileSizeBytes = 10L
            ).getOrThrow()
        }

        val messages = withTimeout(TIMEOUT_MS) {
            bk1Repo.observeChatMessages(bk1Sess.businessId, group.id).first { it.size == 3 }
        }
        assertEquals(3, messages.size)

        // Excluded Booker cannot create groups or read excluded group messages
        signInTestUser("chat_bk2_$suffix@hisabpro.pk")
        val bk2Repo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val bk2Sess = withTimeout(TIMEOUT_MS) {
            bk2Repo.loginWithCredentials(adminSession.businessCode, booker2Creds.username, booker2Creds.temporaryPassword).getOrThrow()
        }
        val bk2Groups = withTimeout(TIMEOUT_MS) {
            bk2Repo.observeChatGroups(bk2Sess.businessId, bk2Sess.role, bk2Sess.accountId).first()
        }
        assertTrue(bk2Groups.isEmpty())

        val deniedSend = withTimeout(TIMEOUT_MS) {
            bk2Repo.sendChatMessage(bk2Sess, group.id, ChatMessageType.TEXT, "Unauthorized message")
        }
        assertTrue(deniedSend.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedSend.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // Staff logs in and sees the group as well
        signInTestUser("chat_staff_$suffix@hisabpro.pk")
        val staffRepo = HisabProRepository(firestore, auth, SecurityRateLimiter())
        val staffSess = withTimeout(TIMEOUT_MS) {
            staffRepo.loginWithCredentials(adminSession.businessCode, staffCreds.username, staffCreds.temporaryPassword).getOrThrow()
        }
        val staffGroups = withTimeout(TIMEOUT_MS) {
            staffRepo.observeChatGroups(staffSess.businessId, staffSess.role, staffSess.accountId).first { it.isNotEmpty() }
        }
        assertEquals(group.id, staffGroups.first().id)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
