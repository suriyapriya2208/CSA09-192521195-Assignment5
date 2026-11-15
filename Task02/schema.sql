CREATE DATABASE IF NOT EXISTS transport_test;
USE transport_test;
CREATE TABLE IF NOT EXISTS users (
    id  VARCHAR(30) PRIMARY KEY,
    pwd VARCHAR(60) NOT NULL   -- plain text ONLY because this is a throw-away test database
);
