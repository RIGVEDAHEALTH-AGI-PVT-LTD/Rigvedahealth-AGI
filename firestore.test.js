const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085,
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

test("unauthenticated user cannot read or write user profiles", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.doc("users/user_123").get());
  await assertFails(unauthedDb.doc("users/user_123").set({ userId: "user_123" }));
});

test("authenticated user can create and read their own patient record with valid ABHA", async () => {
  const user1Db = testEnv.authenticatedContext("user_123").firestore();
  const now = new Date();
  await assertSucceeds(
    user1Db.doc("users/user_123/patients/pat_1").set({
      patientId: "pat_1",
      userId: "user_123",
      abhaNumber: "91-4432-8810-1209",
      name: "Sujatha Reddy",
      age: 58,
      gender: "Female",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(user1Db.doc("users/user_123/patients/pat_1").get());
});

test("user cannot create patient record with invalid ABHA format", async () => {
  const user1Db = testEnv.authenticatedContext("user_123").firestore();
  const now = new Date();
  // Fails with unhyphenated 14 digits
  await assertFails(
    user1Db.doc("users/user_123/patients/pat_bad1").set({
      patientId: "pat_bad1",
      userId: "user_123",
      abhaNumber: "91443288101209",
      name: "Bad Format",
      age: 40,
      gender: "Male",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Fails with arbitrary invalid string
  await assertFails(
    user1Db.doc("users/user_123/patients/pat_bad2").set({
      patientId: "pat_bad2",
      userId: "user_123",
      abhaNumber: "12-34-56",
      name: "Bad Format 2",
      age: 40,
      gender: "Male",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("authenticated user can create and read their own encounter", async () => {
  const user1Db = testEnv.authenticatedContext("user_123").firestore();
  const now = new Date();
  await assertSucceeds(
    user1Db.doc("users/user_123/encounters/enc_1").set({
      encounterId: "enc_1",
      userId: "user_123",
      patientId: "pat_1",
      triageTier: "YELLOW",
      chiefComplaint: "Continuous headache, dizziness, and legs swelling",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(user1Db.doc("users/user_123/encounters/enc_1").get());
});

test("user cannot read another user's encounters", async () => {
  const user1Db = testEnv.authenticatedContext("user_123").firestore();
  const user2Db = testEnv.authenticatedContext("user_456").firestore();
  const now = new Date();
  await assertSucceeds(
    user1Db.doc("users/user_123/encounters/enc_1").set({
      encounterId: "enc_1",
      userId: "user_123",
      patientId: "pat_1",
      triageTier: "YELLOW",
      chiefComplaint: "Continuous headache, dizziness",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(user2Db.doc("users/user_123/encounters/enc_1").get());
});
