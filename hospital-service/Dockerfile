# 构建阶段：在镜像内编译，保证 `docker compose build` 一定包含当前源码。
# 前端 Dockerfile 也是多阶段构建，两边行为保持一致，避免"改了代码 build 完却没生效"。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom 再拉依赖：src 改动不会让依赖层失效，重建时省掉重复下载
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:resolve

COPY src ./src
RUN mvn -B -q -DskipTests package

# 运行阶段：只带 jar，不带 Maven 和源码
FROM eclipse-temurin:17-jre

WORKDIR /app

# 写死 jar 名；若 pom.xml 改了 <version>，这里要同步改
COPY --from=build /app/target/hospital-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# -Duser.timezone 显式指定上海时区：容器默认 UTC，会导致 LocalDateTime.now()
# 与业务时段（SlotTimeUtil 的 8:00/13:00/18:00）错位 8 小时
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
