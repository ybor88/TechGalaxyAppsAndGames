// Client del database (Prisma). © Roberto Di Flumeri
import { PrismaClient, Prisma } from "@prisma/client";

export const db = new PrismaClient({ log: ["warn", "error"] });
export type Tx = Prisma.TransactionClient;
export { Prisma };
