-- ConnectSphere: Auto-creates all 8 service databases on first MySQL startup
CREATE USER IF NOT EXISTS 'connectuser'@'%' IDENTIFIED BY 'root1234';

CREATE DATABASE IF NOT EXISTS connectsphere_auth;
CREATE DATABASE IF NOT EXISTS connectsphere_post;
CREATE DATABASE IF NOT EXISTS connectsphere_comment;
CREATE DATABASE IF NOT EXISTS connectsphere_like;
CREATE DATABASE IF NOT EXISTS connectsphere_follow;
CREATE DATABASE IF NOT EXISTS connectsphere_notification;
CREATE DATABASE IF NOT EXISTS connectsphere_media;
CREATE DATABASE IF NOT EXISTS connectsphere_search;

GRANT ALL PRIVILEGES ON connectsphere_auth.*         TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_post.*         TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_comment.*      TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_like.*         TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_follow.*       TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_notification.* TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_media.*        TO 'connectuser'@'%';
GRANT ALL PRIVILEGES ON connectsphere_search.*       TO 'connectuser'@'%';
FLUSH PRIVILEGES;