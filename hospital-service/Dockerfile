# 运行期镜像：jar 由本机 `mvn package` 打好后 COPY 进来，Docker 只负责运行。
# 不用多阶段构建——在容器里跑 mvn 每次全量拉依赖，慢且没必要。
FROM eclipse-temurin:17-jre

WORKDIR /app

# 写死 jar 名；若 pom.xml 改了 <version>，这里要同步改
COPY target/hospital-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# -Duser.timezone 显式指定上海时区：容器默认 UTC，会导致 LocalDateTime.now()
# 与业务时段（SlotTimeUtil 的 8:00/13:00/18:00）错位 8 小时
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
