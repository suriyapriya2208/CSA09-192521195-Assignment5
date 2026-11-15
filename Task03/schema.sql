CREATE DATABASE IF NOT EXISTS transport_test;
USE transport_test;
DROP TABLE IF EXISTS bus_pass;
CREATE TABLE bus_pass (
    pass_id  VARCHAR(12)   PRIMARY KEY,
    reg_no   VARCHAR(15)   NOT NULL,
    name     VARCHAR(60)   NOT NULL,
    route_no INT           NOT NULL,
    amount   DOUBLE        NOT NULL
);
CREATE INDEX idx_bus_pass_route ON bus_pass(route_no);
