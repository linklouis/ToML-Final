module com.example.toml_final {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.jetbrains.annotations;


    opens toml_final to javafx.fxml;
    exports toml_final;
}