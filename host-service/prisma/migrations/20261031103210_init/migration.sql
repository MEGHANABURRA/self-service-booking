-- CreateEnum
CREATE TYPE "user_role" AS ENUM ('HOST', 'CANDIDATE', 'ADMIN');

-- CreateEnum
CREATE TYPE "day_of_week" AS ENUM ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN');

-- CreateEnum
CREATE TYPE "booking_status" AS ENUM ('BOOKED', 'CANCELLED', 'COMPLETED');

-- CreateTable
CREATE TABLE "users" (
    "user_id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "user_name" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "time_zone" TEXT NOT NULL DEFAULT 'UTC',
    "user_role" "user_role" NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,
    "deleted" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "users_pkey" PRIMARY KEY ("user_id")
);

-- CreateTable
CREATE TABLE "host_pref_details" (
    "id" UUID NOT NULL,
    "host_id" UUID NOT NULL,
    "day" "day_of_week" NOT NULL,
    "start_time" VARCHAR(5) NOT NULL,
    "end_time" VARCHAR(5) NOT NULL,
    "slot_duration_mins" INTEGER NOT NULL,
    "grace_period" INTEGER NOT NULL,
    "max_bookings" INTEGER NOT NULL,
    "deleted" BOOLEAN NOT NULL DEFAULT false,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "host_pref_details_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "booking_data" (
    "booking_id" UUID NOT NULL,
    "slot_start_time" TIMESTAMPTZ NOT NULL,
    "slot_end_time" TIMESTAMPTZ NOT NULL,
    "candidate_id" UUID NOT NULL,
    "host_id" UUID NOT NULL,
    "status" "booking_status" NOT NULL DEFAULT 'BOOKED',
    "cancelled_at" TIMESTAMPTZ,
    "cancel_reason" TEXT,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ NOT NULL,

    CONSTRAINT "booking_data_pkey" PRIMARY KEY ("booking_id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_user_name_key" ON "users"("user_name");

-- CreateIndex
CREATE UNIQUE INDEX "host_pref_details_host_id_day_key" ON "host_pref_details"("host_id", "day");

-- CreateIndex
CREATE INDEX "booking_data_host_id_slot_start_time_idx" ON "booking_data"("host_id", "slot_start_time");

-- AddForeignKey
ALTER TABLE "host_pref_details" ADD CONSTRAINT "host_pref_details_host_id_fkey" FOREIGN KEY ("host_id") REFERENCES "users"("user_id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_data" ADD CONSTRAINT "booking_data_host_id_fkey" FOREIGN KEY ("host_id") REFERENCES "users"("user_id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_data" ADD CONSTRAINT "booking_data_candidate_id_fkey" FOREIGN KEY ("candidate_id") REFERENCES "users"("user_id") ON DELETE RESTRICT ON UPDATE CASCADE;
