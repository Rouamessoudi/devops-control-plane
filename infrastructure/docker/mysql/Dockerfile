FROM mysql:8.4
COPY infrastructure/docker/mysql/init-databases.sh /docker-entrypoint-initdb.d/init-databases.sh
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/init-databases.sh && chmod 755 /docker-entrypoint-initdb.d/init-databases.sh
