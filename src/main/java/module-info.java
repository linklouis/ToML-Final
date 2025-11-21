module com.example.toml_final {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.jetbrains.annotations;


    opens com.example.toml_final to javafx.fxml;
    exports com.example.toml_final;
}