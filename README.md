# taskservice (Lagom evaluation)

## How to Run (Java 8)

```
cd taskservice
sbt clean compile runAll
```
## API

```
Consumes: application/json
Produces: application/json
Valid Status values are either 'Assigned' or 'Completed'
All successful calls currently return 200
POST /task with json-body {name: String, description: String, reporter: String, assignee: String} - create new task
PUT /task/:id with json-body {name: String, description: String, reporter: String, assignee: String, status: String} - update existing task by id
DELETE /task/:id - delete existing task by id
GET /task/:id" - get existing task by id
GET /task?reporter=<value>&assignee=<value>&status=<value> - get all existing tasks satisfying the given filter query parameter values for reporter, assigne and status
```
