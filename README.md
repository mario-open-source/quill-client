# Quill Client

> **A high-performance API client designed for teams working with large collections of requests.**

Quill Client is a fast, lightweight REST API client built with Java Swing, optimized for speed and efficiency when handling extensive API collections. Whether you're managing hundreds of endpoints or working in a team environment, Quill Client delivers the performance you need.

## 🚀 Key Features

### Performance-First Design
- **Optimized Database Schema**: Denormalized structure for lightning-fast queries on frequently accessed fields (URL, method, headers, body)
- **Efficient Indexing**: Strategic indexes on all commonly queried columns for instant lookups
- **Thread Pool Management**: Concurrent request execution with ExecutorService for optimal resource usage
- **SQLite with WAL Mode**: Write-Ahead Logging for better concurrency and performance
- **Shared HTTP Client**: Reused HttpClient instance to minimize connection overhead

### Team Collaboration
- **Postman Collection Support**: Import and work with Postman collections seamlessly
- **Hierarchical Organization**: Nested folders and requests for better organization
- **Fast Search & Navigation**: Color-coded HTTP methods and instant tree navigation
- **Request History**: Built-in database storage for request tracking and history

### Developer Experience
- **Modern Dark Theme**: Beautiful FlatLaf dark theme with customizable colors
- **Intuitive UI**: Clean, organized interface with split panels for efficient workflow
- **Method Color Coding**: Visual HTTP method indicators (GET, POST, PUT, DELETE, etc.) matching Postman's color scheme
- **Rich Response Display**: Formatted JSON/XML responses with syntax highlighting
- **Error Handling**: Comprehensive error messages with HTTP status codes instead of Java exceptions

### API Features
- **All HTTP Methods**: GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD
- **Authentication**: Basic Auth, Bearer Token, JWT Bearer
- **Custom Headers**: Easy header management
- **Query Parameters**: URL parameter support
- **Request Body**: JSON, XML, and raw body support
- **Response Timing**: Request duration tracking

## 📋 Requirements

- **Java 17** or higher
- **Maven 3.6+** (for building)

## 🛠️ Installation

### Clone the Repository
```bash
git clone https://github.com/yourusername/quillclient.git
cd quillclient
```

### Build the Project
```bash
mvn clean install
```

### Run the Application
```bash
mvn exec:java -Dexec.mainClass="com.quillapiclient.Main"
```

Or if you have a JAR file:
```bash
java -jar target/quillclient-1.0-SNAPSHOT.jar
```

## 💻 Usage

### Getting Started

1. **Launch the Application**: Run the main class or JAR file
2. **Import a Collection**: Click "Import" to load a Postman collection JSON file
3. **Select a Request**: Click on any request in the tree to load it into the request panel
4. **Send Requests**: Click "Send" to execute the API call
5. **View Responses**: Responses appear in the bottom panel with formatted output

### Importing Postman Collections

Quill Client fully supports Postman collection format v2.1:
- Navigate to the left panel
- Click the "Import" button
- Select your `.postman_collection.json` file
- Your collection will be loaded with all requests, folders, and configurations

### Making API Calls

1. **Enter URL**: Type or paste your API endpoint
2. **Select Method**: Choose from GET, POST, PUT, DELETE, PATCH, OPTIONS, or HEAD
3. **Configure Headers**: Add custom headers in the Headers tab
4. **Set Authentication**: Configure auth in the Authorization tab
5. **Add Body**: Enter request body in the Body tab (for POST/PUT/PATCH)
6. **Add Query Params**: Set query parameters in the Params tab
7. **Send**: Click the "Send" button to execute

### Performance Tips

- **Large Collections**: Quill Client handles large collections efficiently thanks to optimized database indexing
- **Concurrent Requests**: Multiple requests can be executed simultaneously using the thread pool
- **Fast Navigation**: Use the tree view to quickly navigate through hundreds of requests
- **Search**: The indexed database allows for fast searching and filtering

## 🏗️ Architecture

### Technology Stack
- **Java 17**: Modern Java features and performance
- **Java Swing**: Native desktop UI framework
- **FlatLaf**: Modern look and feel with dark theme
- **SQLite**: Lightweight, fast embedded database
- **Jackson**: JSON processing
- **Java HTTP Client**: Built-in HTTP client (Java 11+)

### Database Schema

The application uses an optimized SQLite schema designed for performance:

- **collections**: Collection metadata
- **items**: Hierarchical items/folders with parent relationships
- **requests**: Denormalized request data for fast access
- **headers**: Request headers (one-to-many)
- **query_params**: Query parameters (one-to-many)
- **variables**: Collection and item-level variables
- **events**: Pre-request and test scripts

**Performance Optimizations:**
- Frequently accessed fields (URL, method, body) stored directly in requests table
- Strategic indexes on all commonly queried columns
- Foreign keys with CASCADE deletes for data integrity
- WAL mode for better concurrency

### Project Structure

```
quillclient/
├── src/main/java/com/quillapiclient/
│   ├── components/          # UI components
│   ├── controller/          # Business logic controllers
│   ├── db/                  # Database layer
│   ├── objects/             # Data models
│   ├── server/              # HTTP client and API handling
│   └── utility/             # Utility classes
└── pom.xml                  # Maven configuration
```

## 🎨 Customization

### Theme Colors

The application uses a centralized color theme system. Modify colors in `AppColorTheme.java`:

```java
// Example: Change panel background
public static final Color PANEL_BACKGROUND = new Color(30, 30, 30);
```

### HTTP Method Colors

Method colors match Postman's color scheme and can be customized in `MethodColorUtil.java`.

## 🐛 Troubleshooting

### Database Issues
- Database is stored at `~/.quillclient/app.db` (Linux/Mac) or `%USERPROFILE%\.quillclient\app.db` (Windows)
- If you encounter database errors, delete the database file and restart the application

### Connection Issues
- Check your network connection
- Verify the API endpoint URL
- Review firewall settings
- Check SSL certificate validity

### Performance Issues
- Ensure you're using Java 17 or higher
- Close unused collections to free memory
- Check available disk space for the database

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Built with [FlatLaf](https://www.formdev.com/flatlaf/) for the modern UI
- Uses [SQLite](https://www.sqlite.org/) for fast, embedded database storage
- Inspired by Postman's excellent API client design

## 📧 Support

For issues, questions, or suggestions, please open an issue on GitHub.

---

**Built for speed. Built for teams. Built for scale.**

