INSERT INTO category (name, created_at, updated_at)
VALUES ('신선식품', NOW(), NOW()),
       ('가공식품', NOW(), NOW()),
       ('생활용품', NOW(), NOW()),
       ('전자제품', NOW(), NOW()),
       ('도서·문구', NOW(), NOW()),
       ('뷰티', NOW(), NOW()),
       ('유아동', NOW(), NOW()),
       ('반려동물', NOW(), NOW())
    ON DUPLICATE KEY UPDATE name = name;