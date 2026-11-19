package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Scanner;

public class Main {
    private static final String URL = "jdbc:mysql://localhost:3306/ecommerce_db";
    // Replace these values with your own MySQL username and password.
    private static final String USER = "root";
    private static final String PASSWORD = "Lokesh@888";

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== Order Management System ===");
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            System.out.println("Connected to ecommerce_db successfully.");
            boolean running = true;
            while (running) {
                showMenu();
                int choice = readInt("Choose an option: ");
                try {
                    switch (choice) {
                        case 1 -> insertThreeOrders(connection);
                        case 2 -> searchOrdersByStatus(connection);
                        case 3 -> increaseQuantity(connection);
                        case 4 -> updateOrderStatus(connection);
                        case 5 -> showTotalAndDeleteCancelled(connection);
                        case 6 -> running = false;
                        default -> System.out.println("Invalid menu option. Choose 1 to 6.");
                    }
                } catch (SQLException e) {
                    System.out.println("Database operation failed: " + e.getMessage());
                }
            }
            System.out.println("Program ended.");
        } catch (SQLException e) {
            System.out.println("Could not connect to MySQL: " + e.getMessage());
            System.out.println("Check that MySQL is running, the database exists, and URL/username/password are correct.");
        }
    }

    private static void showMenu() {
        System.out.println("\n1. Insert three orders");
        System.out.println("2. Search orders by status (quantity > 2)");
        System.out.println("3. Increase order quantity");
        System.out.println("4. Update order status");
        System.out.println("5. Show total order value and delete a Cancelled order");
        System.out.println("6. Exit");
    }

    private static void insertThreeOrders(Connection connection) {
        for (int i = 1; i <= 3; i++) {
            System.out.println("\nEnter details for order " + i + " of 3:");
            int id = readInt("Order ID: ");
            String customer = readText("Customer name: ");
            String product = readText("Product name: ");
            int quantity = readPositiveInt("Quantity (greater than 0): ");
            double price = readPositiveDouble("Unit price (greater than 0): ");
            String status = readText("Status (for example Pending, Shipped, Delivered, Cancelled): ");
            String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                statement.setString(2, customer);
                statement.setString(3, product);
                statement.setInt(4, quantity);
                statement.setDouble(5, price);
                statement.setString(6, status);
                statement.executeUpdate();
                System.out.println("Order " + id + " inserted successfully.");
            } catch (SQLIntegrityConstraintViolationException e) {
                System.out.println("Could not insert order " + id + ": that order ID already exists.");
            } catch (SQLException e) {
                System.out.println("Could not insert order " + id + ": " + e.getMessage());
            }
        }
    }

    private static void searchOrdersByStatus(Connection connection) throws SQLException {
        String status = readText("Enter status to search: ");
        String sql = "SELECT order_id, customer_name, product_name, quantity, unit_price, status "
                + "FROM Orders WHERE status = ? AND quantity > 2 ORDER BY unit_price DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            try (ResultSet result = statement.executeQuery()) {
                boolean found = false;
                while (result.next()) {
                    found = true;
                    printOrder(result);
                }
                if (!found) System.out.println("No matching orders found.");
            }
        }
    }

    private static void increaseQuantity(Connection connection) throws SQLException {
        int id = readInt("Enter order ID: ");
        int additional = readPositiveInt("Enter additional quantity (greater than 0): ");
        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, additional);
            statement.setInt(2, id);
            int rows = statement.executeUpdate();
            if (rows == 0) {
                System.out.println("Order " + id + " does not exist.");
            } else {
                try (PreparedStatement query = connection.prepareStatement("SELECT quantity FROM Orders WHERE order_id = ?")) {
                    query.setInt(1, id);
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) System.out.println("Quantity updated. New quantity: " + result.getInt("quantity"));
                    }
                }
            }
        }
    }

    private static void updateOrderStatus(Connection connection) throws SQLException {
        int id = readInt("Enter order ID: ");
        String newStatus = readText("Enter new status (Pending, Shipped, Delivered): ");
        if (!newStatus.equalsIgnoreCase("Pending") && !newStatus.equalsIgnoreCase("Shipped")
                && !newStatus.equalsIgnoreCase("Delivered")) {
            System.out.println("Invalid status. Use Pending, Shipped, or Delivered.");
            return;
        }
        String checkSql = "SELECT order_id FROM Orders WHERE order_id = ?";
        try (PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setInt(1, id);
            try (ResultSet result = check.executeQuery()) {
                if (!result.next()) {
                    System.out.println("Order " + id + " does not exist.");
                    return;
                }
            }
        }
        String updateSql = "UPDATE Orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            update.setString(1, normalizeStatus(newStatus));
            update.setInt(2, id);
            update.executeUpdate();
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM Orders WHERE order_id = ?")) {
            query.setInt(1, id);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    System.out.println("Status updated successfully. Updated order:");
                    printOrder(result);
                }
            }
        }
    }

    private static void showTotalAndDeleteCancelled(Connection connection) throws SQLException {
        String totalSql = "SELECT SUM(quantity * unit_price) AS total_value FROM Orders";
        try (PreparedStatement statement = connection.prepareStatement(totalSql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            double total = result.getDouble("total_value");
            if (result.wasNull()) total = 0.0;
            System.out.printf("Total order value: %.2f%n", total);
        }

        int id = readInt("Enter order ID to delete (only Cancelled orders can be deleted): ");
        String checkSql = "SELECT status FROM Orders WHERE order_id = ?";
        try (PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setInt(1, id);
            try (ResultSet result = check.executeQuery()) {
                if (!result.next()) {
                    System.out.println("Order " + id + " does not exist.");
                    return;
                }
                if (!"Cancelled".equalsIgnoreCase(result.getString("status"))) {
                    System.out.println("Order is not Cancelled; it was not deleted.");
                    return;
                }
            }
        }
        String deleteSql = "DELETE FROM Orders WHERE order_id = ? AND status = ?";
        try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            delete.setInt(1, id);
            delete.setString(2, "Cancelled");
            int rows = delete.executeUpdate();
            if (rows > 0) System.out.println("Cancelled order " + id + " deleted successfully.");
            else System.out.println("Order was not deleted. Check its current status and try again.");
        }
    }

    private static void printOrder(ResultSet result) throws SQLException {
        System.out.printf("ID: %d | Customer: %s | Product: %s | Quantity: %d | Unit price: %.2f | Status: %s%n",
                result.getInt("order_id"), result.getString("customer_name"), result.getString("product_name"),
                result.getInt("quantity"), result.getDouble("unit_price"), result.getString("status"));
    }

    private static String normalizeStatus(String status) {
        if (status.equalsIgnoreCase("pending")) return "Pending";
        if (status.equalsIgnoreCase("shipped")) return "Shipped";
        return "Delivered";
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try { return Integer.parseInt(value); }
            catch (NumberFormatException e) { System.out.println("Enter a whole number."); }
        }
    }

    private static int readPositiveInt(String prompt) {
        while (true) {
            int value = readInt(prompt);
            if (value > 0) return value;
            System.out.println("Value must be greater than zero.");
        }
    }

    private static double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                double number = Double.parseDouble(value);
                if (Double.isFinite(number) && number > 0) return number;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a valid price greater than zero.");
        }
    }

    private static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This value cannot be empty.");
        }
    }
}
