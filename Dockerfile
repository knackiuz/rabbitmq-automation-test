# Use lightweight maven image with JDK 17 as the build environment
FROM maven:3.8.5-openjdk-17-slim

# Set the working directory inside the container
WORKDIR /app

# Copy only pom.xml first to leverage docker layer catching the dependencies
COPY pom.xml .

# Download project dependencies without building the application (improves build speed)
RUN mvn dependency:go-offline -B

# Copy the entire source code into the container
COPY src ./src

# Pre-compile the tests so the bytecode is stored in the image layer
RUN mvn test-compile

# Set the default command to execute the test suite when the containers starts
CMD ["mvn", "test"]
