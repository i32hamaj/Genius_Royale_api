# --- FASE 1: Construir el .jar ---
# Usamos una imagen de Maven con Java 17 para compilar
FROM maven:3.9-eclipse-temurin-17-focal AS build

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar los archivos de Maven y descargar dependencias (para cache)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline

# Copiar el resto del código fuente y construir el .jar
COPY src src
RUN ./mvnw clean package -DskipTests

# --- FASE 2: Crear la imagen final de ejecución ---
# Usamos una imagen ligera que solo tiene Java 17 para ejecutar
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Copiar el .jar que construimos en la FASE 1
COPY --from=build /app/target/*.jar app.jar

# Render asignará un puerto, pero es bueno exponer el 8081
EXPOSE 8081

# El comando para arrancar la API (reemplaza al "Start Command" de Render)
CMD ["java", "-jar", "app.jar"]