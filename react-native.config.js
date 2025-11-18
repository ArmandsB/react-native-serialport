module.exports = {
  dependencies: {
    "react-native-serialport": {
      root: __dirname,
      platforms: {
        ios: null, // no iOS implementation
        android: {
          sourceDir: "./android"
        }
      }
    }
  }
};