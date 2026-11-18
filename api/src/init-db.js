import { defaultDatabasePath, openDatabase } from './db.js';

const database = openDatabase();
database.close();
console.log(`Initialized SQLite database at ${defaultDatabasePath}`);
