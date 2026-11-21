const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_google_uid_1";
const STAFF_UID = "staff_google_uid_2";
const BOOKER_A_UID = "booker_a_google_uid_3";
const BOOKER_B_UID = "booker_b_google_uid_4";
const OUTSIDER_UID = "outsider_google_uid_5";

const BIZ_1 = "biz_lahore_1";
const BIZ_2 = "biz_karachi_2";
const BOOKER_A_ID = "bkr_ali_1";
const BOOKER_B_ID = "bkr_bilal_2";
const STAFF_ID = "usr_staff_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsPast() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedBusinessWorkspace() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = tsPast();

    await db.collection("businesses").doc(BIZ_1).set({
      businessName: "Al-Noor Traders",
      businessCode: "HP-1001",
      ownerUid: ADMIN_UID,
      ownerName: "Tariq Mahmood",
      ownerMobile: "03001234567",
      ownerEmail: "tariq@alnoor.pk",
      currency: "PKR",
      timezone: "Asia/Karachi",
      allowOverRecovery: false,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("members").doc(ADMIN_UID).set({
      businessId: BIZ_1,
      googleUid: ADMIN_UID,
      accountId: "adm_1",
      username: "admin",
      fullName: "Tariq Mahmood",
      role: "SUPER_ADMIN",
      bookerId: "",
      status: "ACTIVE",
      claimedTicketId: "hp-1001__admin",
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("members").doc(STAFF_UID).set({
      businessId: BIZ_1,
      googleUid: STAFF_UID,
      accountId: STAFF_ID,
      username: "usman_staff",
      fullName: "Usman Ali",
      role: "STAFF",
      bookerId: "",
      status: "ACTIVE",
      claimedTicketId: "hp-1001__usman_staff",
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("permissions").doc(STAFF_ID).set({
      businessId: BIZ_1,
      userId: STAFF_ID,
      viewDashboard: true,
      viewShops: true,
      addShops: true,
      editShops: false,
      addCredit: true,
      editCredit: false,
      recordRecovery: false,
      viewRecoveryHistory: true,
      viewBookers: true,
      manageBookers: false,
      viewReports: true,
      generatePdfs: true,
      searchShops: true,
      viewActivityLogs: false,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("members").doc(BOOKER_A_UID).set({
      businessId: BIZ_1,
      googleUid: BOOKER_A_UID,
      accountId: BOOKER_A_ID,
      username: "ali_booker",
      fullName: "Ali Raza",
      role: "BOOKER",
      bookerId: BOOKER_A_ID,
      status: "ACTIVE",
      claimedTicketId: "hp-1001__ali_booker",
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("shops").doc("shop_1").set({
      businessId: BIZ_1,
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      ownerName: "Haji Akram",
      mobileNumber: "03219876543",
      address: "Main Bazar",
      city: "Lahore",
      openingCreditPaisa: 0,
      openingDateMillis: Date.now() - 5000,
      notes: "",
      isArchived: false,
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("shops").doc("shop_2").set({
      businessId: BIZ_1,
      shopUid: "SHP-1002",
      shopName: "Bismillah Store",
      bookerId: BOOKER_B_ID,
      bookerName: "Bilal Khan",
      ownerName: "Bilal Ahmed",
      mobileNumber: "03331112223",
      address: "Gulberg III",
      city: "Lahore",
      openingCreditPaisa: 0,
      openingDateMillis: Date.now() - 5000,
      notes: "",
      isArchived: false,
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_1").set({
      businessId: BIZ_1,
      txCode: "CR-1001",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 5000000,
      transactionKind: "CREDIT",
      relatedTxId: "",
      reason: "",
      invoiceNumber: "INV-01",
      description: "Initial stock",
      attachmentUri: "",
      txDateMillis: Date.now() - 4000,
      txDateFormatted: "16 Nov 2026",
      txTimeFormatted: "03:00 PM",
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdByRole: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated caller cannot read businesses or shops", async () => {
  await seedBusinessWorkspace();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("businesses").doc(BIZ_1).get());
  await assertFails(unauth.collection("businesses").doc(BIZ_1).collection("shops").get());
});

test("Cross-business outsider cannot read shops or transactions", async () => {
  await seedBusinessWorkspace();
  const outsider = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsider.collection("businesses").doc(BIZ_1).get());
  await assertFails(outsider.collection("businesses").doc(BIZ_1).collection("shops").get());
  await assertFails(outsider.collection("businesses").doc(BIZ_1).collection("credit_transactions").get());
});

test("Login tickets cannot be listed by anyone (prevents username enumeration)", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(adminDb.collection("login_tickets").get());
});

test("Super Admin can create and list shops and add credit/recovery", async () => {
  await seedBusinessWorkspace();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = tsPast();

  await assertSucceeds(adminDb.collection("businesses").doc(BIZ_1).collection("shops").get());
  await assertSucceeds(
    adminDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_1").set({
      businessId: BIZ_1,
      txCode: "RC-1001",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 2000000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Part payment",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "16 Nov 2026",
      txTimeFormatted: "03:15 PM",
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdByRole: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Booker can list ONLY their assigned shops via filtered query and fails unfiltered or other Booker shops", async () => {
  await seedBusinessWorkspace();
  const bookerADb = testEnv.authenticatedContext(BOOKER_A_UID).firestore();

  // Filtered query matching Booker A's bookerId succeeds
  await assertSucceeds(
    bookerADb.collection("businesses").doc(BIZ_1).collection("shops")
      .where("bookerId", "==", BOOKER_A_ID)
      .get()
  );

  // Unfiltered query fails
  await assertFails(
    bookerADb.collection("businesses").doc(BIZ_1).collection("shops").get()
  );

  // Direct read of Booker B's shop fails
  await assertFails(
    bookerADb.collection("businesses").doc(BIZ_1).collection("shops").doc("shop_2").get()
  );

  // Booker cannot create a shop or add credit
  const now = tsPast();
  await assertFails(
    bookerADb.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_hack").set({
      businessId: BIZ_1,
      txCode: "CR-9999",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 1000,
      transactionKind: "CREDIT",
      relatedTxId: "",
      reason: "",
      invoiceNumber: "",
      description: "Hack",
      attachmentUri: "",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "16 Nov 2026",
      txTimeFormatted: "03:15 PM",
      createdByAccountId: BOOKER_A_ID,
      createdByName: "Ali Raza",
      createdByRole: "BOOKER",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Staff permissions enforced: permitted addCredit succeeds, unpermitted recordRecovery fails", async () => {
  await seedBusinessWorkspace();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const now = tsPast();

  // Staff has addCredit = true
  await assertSucceeds(
    staffDb.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_staff").set({
      businessId: BIZ_1,
      txCode: "CR-1002",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 1500000,
      transactionKind: "CREDIT",
      relatedTxId: "",
      reason: "",
      invoiceNumber: "",
      description: "Staff credit",
      attachmentUri: "",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "16 Nov 2026",
      txTimeFormatted: "03:20 PM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Staff has recordRecovery = false
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff").set({
      businessId: BIZ_1,
      txCode: "RC-1002",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 100000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Unauthorized recovery",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "16 Nov 2026",
      txTimeFormatted: "03:20 PM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Financial transactions are immutable: even Super Admin cannot update or delete credit_transactions", async () => {
  await seedBusinessWorkspace();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(
    adminDb.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_1").update({
      amountPaisa: 100,
    })
  );
  await assertFails(
    adminDb.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_1").delete()
  );
});

test("Team Meeting Group Chat: Super Admin creates group, members exchange messages, non-members denied", async () => {
  await seedBusinessWorkspace();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const bookerADb = testEnv.authenticatedContext(BOOKER_A_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = tsPast();

  // Super Admin creates a group with Booker A (and NOT Staff)
  await assertSucceeds(
    adminDb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1").set({
      businessId: BIZ_1,
      name: "Booker Recovery Sync",
      description: "Daily update",
      memberAccountIds: ["adm_1", BOOKER_A_ID],
      memberNames: ["Tariq Mahmood", "Ali Raza"],
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      lastMessagePreview: "Created",
      lastMessageAtMillis: Date.now() - 500,
      isArchived: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Booker A cannot create a group
  await assertFails(
    bookerADb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_hack").set({
      businessId: BIZ_1,
      name: "Unauthorized Group",
      description: "",
      memberAccountIds: [BOOKER_A_ID],
      memberNames: ["Ali Raza"],
      createdByAccountId: BOOKER_A_ID,
      createdByName: "Ali Raza",
      lastMessagePreview: "",
      lastMessageAtMillis: Date.now() - 500,
      isArchived: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Booker A (member) can send a message and read group messages
  await assertSucceeds(
    bookerADb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1")
      .collection("messages").doc("msg_1").set({
        businessId: BIZ_1,
        groupId: "grp_1",
        senderAccountId: BOOKER_A_ID,
        senderName: "Ali Raza",
        senderRole: "Booker",
        messageType: "TEXT",
        text: "Collected Rs. 15,000 from Madina Mart",
        mediaBase64: "",
        fileName: "",
        mimeType: "",
        durationSec: 0,
        fileSizeBytes: 0,
        sentAtMillis: Date.now() - 200,
        dateFormatted: "16 Nov 2026",
        timeFormatted: "03:30 PM",
        createdAt: now,
      })
  );
  await assertSucceeds(
    bookerADb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1")
      .collection("messages").get()
  );

  // Staff (not added to grp_1) and Outsider cannot read or send in grp_1
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1").get()
  );
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1")
      .collection("messages").get()
  );
  await assertFails(
    outsiderDb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1")
      .collection("messages").get()
  );

  // Messages are immutable (cannot be updated or deleted even by Super Admin)
  await assertFails(
    adminDb.collection("businesses").doc(BIZ_1).collection("chat_groups").doc("grp_1")
      .collection("messages").doc("msg_1").delete()
  );
});

test("Notifications: Super Admin and Booker can mark their own notifications read; cross-booker blocked", async () => {
  await seedBusinessWorkspace();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const bookerADb = testEnv.authenticatedContext(BOOKER_A_UID).firestore();
  const now = tsPast();

  await assertSucceeds(
    adminDb.collection("businesses").doc(BIZ_1).collection("notifications").doc("ntf_1").set({
      businessId: BIZ_1,
      title: "New Credit Added",
      message: "Rs. 5,000 recorded for Madina Mart",
      category: "CREDIT",
      targetRole: "BOOKER",
      targetBookerId: BOOKER_A_ID,
      shopUid: "SHP-1001",
      isRead: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Booker A can query their own unread notifications and mark as read
  await assertSucceeds(
    bookerADb.collection("businesses").doc(BIZ_1).collection("notifications")
      .where("targetBookerId", "==", BOOKER_A_ID)
      .where("isRead", "==", false)
      .get()
  );
  await assertSucceeds(
    bookerADb.collection("businesses").doc(BIZ_1).collection("notifications").doc("ntf_1").update({
      isRead: true,
      updatedAt: tsPast(),
    })
  );

  // Booker cannot tamper with notification message
  await assertFails(
    bookerADb.collection("businesses").doc(BIZ_1).collection("notifications").doc("ntf_1").update({
      message: "Tampered",
      updatedAt: tsPast(),
    })
  );
});

test("3-Company Strict Isolation: Three distinct companies cannot read or write each other's records", async () => {
  await seedBusinessWorkspace();
  const ADMIN_2_UID = "admin_company2_uid";
  const ADMIN_3_UID = "admin_company3_uid";
  const BIZ_3 = "biz_islamabad_3";
  const now = tsPast();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [bId, admUid, code, name, shId, sUid] of [
      [BIZ_2, ADMIN_2_UID, "HP-2002", "Karachi Distributors", "shop_biz2", "SHP-2001"],
      [BIZ_3, ADMIN_3_UID, "HP-3003", "Islamabad Traders", "shop_biz3", "SHP-3001"],
    ]) {
      await db.collection("businesses").doc(bId).set({
        businessName: name,
        businessCode: code,
        ownerUid: admUid,
        ownerName: "Owner " + code,
        ownerMobile: "03001119999",
        ownerEmail: "owner@example.pk",
        currency: "PKR",
        timezone: "Asia/Karachi",
        allowOverRecovery: false,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      });
      await db.collection("businesses").doc(bId).collection("members").doc(admUid).set({
        businessId: bId,
        googleUid: admUid,
        accountId: "adm_" + bId,
        username: "admin_" + code,
        fullName: "Admin " + code,
        role: "SUPER_ADMIN",
        bookerId: "",
        status: "ACTIVE",
        claimedTicketId: code.toLowerCase() + "__admin",
        createdAt: now,
        updatedAt: now,
      });
      await db.collection("businesses").doc(bId).collection("shops").doc(shId).set({
        businessId: bId,
        shopUid: sUid,
        shopName: name + " Shop",
        bookerId: "bk_" + bId,
        bookerName: "Booker " + code,
        ownerName: "Customer",
        mobileNumber: "03009998888",
        address: "Main Road",
        city: "Karachi",
        openingCreditPaisa: 100000,
        openingDateMillis: Date.now() - 5000,
        notes: "",
        isArchived: false,
        createdByAccountId: "adm_" + bId,
        createdByName: "Admin " + code,
        createdAt: now,
        updatedAt: now,
      });
    }
  });

  const admin1Db = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const admin2Db = testEnv.authenticatedContext(ADMIN_2_UID).firestore();
  const admin3Db = testEnv.authenticatedContext(ADMIN_3_UID).firestore();

  // Each company reads only its own shops
  await assertSucceeds(admin1Db.collection("businesses").doc(BIZ_1).collection("shops").get());
  await assertSucceeds(admin2Db.collection("businesses").doc(BIZ_2).collection("shops").get());
  await assertSucceeds(admin3Db.collection("businesses").doc(BIZ_3).collection("shops").get());

  // Cross-company reads/writes across all 3 pairs fail
  await assertFails(admin1Db.collection("businesses").doc(BIZ_2).collection("shops").get());
  await assertFails(admin1Db.collection("businesses").doc(BIZ_3).collection("shops").get());
  await assertFails(admin2Db.collection("businesses").doc(BIZ_1).collection("credit_transactions").get());
  await assertFails(admin2Db.collection("businesses").doc(BIZ_3).collection("shops").doc("shop_biz3").get());
  await assertFails(admin3Db.collection("businesses").doc(BIZ_1).get());
  await assertFails(admin3Db.collection("businesses").doc(BIZ_2).collection("audit_logs").get());
});

test("Staff same-day recovery allowed when permitted; Staff backdate, correction & reversal denied while Admin allowed", async () => {
  await seedBusinessWorkspace();
  const now = tsPast();

  // Enable recordRecovery for Staff
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("businesses").doc(BIZ_1).collection("permissions").doc(STAFF_ID).update({
      recordRecovery: true,
    });
  });

  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Staff same-day non-backdated RECOVERY succeeds
  await assertSucceeds(
    staffDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff_ok").set({
      businessId: BIZ_1,
      txCode: "RC-2001",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 500000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Same day collection",
      isBackdated: false,
      correctedFromTxId: "",
      correctionGroupId: "",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "18 Nov 2026",
      txTimeFormatted: "10:00 AM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Staff backdated recovery (5 days ago) FAILS
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff_backdate").set({
      businessId: BIZ_1,
      txCode: "RC-2002",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 500000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Backdate try",
      isBackdated: true,
      correctedFromTxId: "",
      correctionGroupId: "",
      txDateMillis: Date.now() - 5 * 86400000,
      txDateFormatted: "13 Nov 2026",
      txTimeFormatted: "10:00 AM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Staff future-dated recovery FAILS
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff_future").set({
      businessId: BIZ_1,
      txCode: "RC-2003",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 500000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Future try",
      isBackdated: false,
      correctedFromTxId: "",
      correctionGroupId: "",
      txDateMillis: Date.now() + 2 * 86400000,
      txDateFormatted: "20 Nov 2026",
      txTimeFormatted: "10:00 AM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Staff RECOVERY_REVERSAL FAILS
  await assertFails(
    staffDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff_rev").set({
      businessId: BIZ_1,
      txCode: "REV-2001",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 500000,
      transactionKind: "RECOVERY_REVERSAL",
      relatedTxId: "rc_staff_ok",
      reason: "Staff tried reversal",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "",
      txDateMillis: Date.now() - 500,
      txDateFormatted: "18 Nov 2026",
      txTimeFormatted: "10:05 AM",
      createdByAccountId: STAFF_ID,
      createdByName: "Usman Ali",
      createdByRole: "STAFF",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Business Admin CAN post backdated recovery and RECOVERY_REVERSAL, but CANNOT delete recovery_transactions
  await assertSucceeds(
    adminDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_admin_back").set({
      businessId: BIZ_1,
      txCode: "RC-2004",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 300000,
      transactionKind: "RECOVERY",
      relatedTxId: "",
      reason: "",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "Admin authorized historical date",
      isBackdated: true,
      correctedFromTxId: "",
      correctionGroupId: "",
      txDateMillis: Date.now() - 5 * 86400000,
      txDateFormatted: "13 Nov 2026",
      txTimeFormatted: "02:00 PM",
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdByRole: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_admin_rev").set({
      businessId: BIZ_1,
      txCode: "REV-2002",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 500000,
      transactionKind: "RECOVERY_REVERSAL",
      relatedTxId: "rc_staff_ok",
      reason: "Entered wrong amount",
      paymentMethod: "Cash",
      referenceNumber: "",
      notes: "",
      txDateMillis: Date.now() - 500,
      txDateFormatted: "18 Nov 2026",
      txTimeFormatted: "10:10 AM",
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdByRole: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    adminDb.collection("businesses").doc(BIZ_1).collection("recovery_transactions").doc("rc_staff_ok").delete()
  );
});

test("Platform Owner Verified Email Access vs Unauthorized Business Admin / Unverified Caller", async () => {
  await seedBusinessWorkspace();
  const now = tsPast();

  const ownerDb = testEnv.authenticatedContext("platform_owner_uid", {
    email: "muhammadtufailllc007@gmail.com",
    email_verified: true,
  }).firestore();

  const unverifiedOwnerDb = testEnv.authenticatedContext("platform_owner_unverified", {
    email: "muhammadtufailllc007@gmail.com",
    email_verified: false,
  }).firestore();

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "tariq@alnoor.pk",
    email_verified: true,
  }).firestore();

  // Business Admin and unverified spoof cannot read or write platform_owner or platform_activity
  await assertFails(adminDb.collection("platform_owner").doc("root").get());
  await assertFails(adminDb.collection("platform_activity").get());
  await assertFails(unverifiedOwnerDb.collection("platform_owner").doc("root").get());

  // Verified Platform Owner creates platform_owner/root, logs activity, creates backup snapshot, and suspends company
  await assertSucceeds(
    ownerDb.collection("platform_owner").doc("root").set({
      username: "MrTufailadmin",
      authorizedEmail: "muhammadtufailllc007@gmail.com",
      passwordSalt: "c2FsdF92YWx1ZV8xMjM=",
      passwordHash: "aGFzaF92YWx1ZV8xMjM0NTY3ODkwMTI=",
      mustChangePassword: false,
      sessionRevokedBeforeMillis: 0,
      lastLoginMillis: Date.now() - 1000,
      deviceCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("platform_activity").doc("plog_1").set({
      actorEmail: "muhammadtufailllc007@gmail.com",
      actorUsername: "MrTufailadmin",
      action: "OWNER_LOGIN",
      targetBusinessId: "",
      targetBusinessName: "",
      targetAccountId: "",
      targetUsername: "",
      details: "Owner authenticated",
      timestampMillis: Date.now() - 500,
      createdAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("platform_backups").doc("bkp_1").set({
      createdByEmail: "muhammadtufailllc007@gmail.com",
      createdAtMillis: Date.now() - 500,
      createdAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("platform_backups").doc("bkp_1").collection("companies").doc(BIZ_1).set({
      businessId: BIZ_1,
      snapshotJson: "{\"businessName\":\"Al-Noor Traders\"}",
      createdAt: now,
    })
  );

  // Suspend company while preserving its records
  await assertSucceeds(
    ownerDb.collection("businesses").doc(BIZ_1).update({
      status: "SUSPENDED",
      suspendedReason: "Audit verification",
      updatedAt: tsPast(),
    })
  );

  // While suspended, Business Admin cannot post new credit transactions
  await assertFails(
    adminDb.collection("businesses").doc(BIZ_1).collection("credit_transactions").doc("cr_while_suspended").set({
      businessId: BIZ_1,
      txCode: "CR-SUSP-1",
      shopId: "shop_1",
      shopUid: "SHP-1001",
      shopName: "Madina Mart",
      bookerId: BOOKER_A_ID,
      bookerName: "Ali Raza",
      amountPaisa: 100000,
      transactionKind: "CREDIT",
      relatedTxId: "",
      reason: "",
      invoiceNumber: "",
      description: "Blocked during suspension",
      attachmentUri: "",
      txDateMillis: Date.now() - 1000,
      txDateFormatted: "18 Nov 2026",
      txTimeFormatted: "11:00 AM",
      createdByAccountId: "adm_1",
      createdByName: "Tariq Mahmood",
      createdByRole: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Business Admin cannot read backups
  await assertFails(adminDb.collection("platform_backups").get());
});

