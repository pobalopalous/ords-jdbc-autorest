# Universal RESTful Database Access
RESTful interface to DB/2, MySQL, Oracle, PostgreSQL, and SQL Server with Standard JDBC and Oracle REST Data Services (ORDS https://oracle.com/rest). No installation of ORDS required for the target database.

![ORDS plugin that can use vendor specific JDBC drivers to provide RESTful interface to tables and views](images/ords-autorest-jdbc-plugin.png)

## ORDS AutoREST
Oracle REST Data Services (ORDS) AutoREST is a feature that allows you to expose database tables and views as RESTful web services automatically, without writing custom REST handlers or PL/SQL code. This makes it easy to build RESTful APIs over Oracle Database objects with minimal effort.

### How ORDS AutoREST works
When AutoREST is enabled for a table or view, ORDS automatically generates RESTful endpoints that allow you to:

- Retrieve data (GET)
- Insert new records (POST)
- Update existing records (PUT/PATCH)
- Delete records (DELETE)
ORDS provides these capabilities with built-in pagination, filtering, and security options.

### Advantages of ORDS AutoREST
✔ Zero-code API – No need to write PL/SQL or Java.

✔ CRUD-ready – Supports full Create, Read, Update, Delete.

✔ Built-in security – Can integrate with OAuth, JWT, or DB authentication.

✔ Scalable – Works efficiently with Oracle Database performance features.

✔ Metadata-driven – Automatically updates when the schema changes.

### Limitations of ORDS AutoREST
The AutoREST feature does require ORDS to be installed to the target database and currently only works with the Oracle database. 
![ORDS AutoREST only works with Oracle database and it must be installed in that target database](images/ords-autorest-requires-db-installation.png)

ORDS is an extensible product though. The code in this repository creates a plugin jar which provides a standards based mechanism to achieve that RESTful interface to the tables and views in the top 5 relational databases using vendor specific JDBC drivers.
