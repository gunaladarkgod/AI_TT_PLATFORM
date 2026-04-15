-- Default admin for local/dev (legacy password scheme: MD5(plain + 'xglszm'))
-- Plain password: admin123  -> hash e7e0c210fef662fa9dd4cc68bf14fa67
INSERT INTO usr_user (username, password_hash, nickname, status)
VALUES ('admin', 'e7e0c210fef662fa9dd4cc68bf14fa67', 'Administrator', 1);

-- V2 seeds ADMIN as first row (id = 1)
INSERT INTO usr_user_role (user_id, role_id) VALUES (1, 1);
