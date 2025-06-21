import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.event.ListSelectionEvent;
import java.util.List;

/**
 * Full Exam System with scheduled quizzes.
 */
class ExamSystem {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginFrame::new);
    }

    // Models
    static abstract class User implements Serializable {
        private static final long serialVersionUID = 1L;
        enum Role { TEACHER, STUDENT }
        String username, name, passwordHash;
        Role role;

        public User(String username, String name, String passwordHash, Role role) {
            this.username = username;
            this.name = name;
            this.passwordHash = passwordHash;
            this.role = role;
        }

        public String getUsername() { return username; }
        public String getName() { return name; }
        public String getPasswordHash() { return passwordHash; }
        public Role getRole() { return role; }
    }

    static class Teacher extends User {
        private static final long serialVersionUID = 1L;
        Teacher(String username, String name, String passwordHash) {
            super(username, name, passwordHash, Role.TEACHER);
        }
    }

    static class Student extends User {
        private static final long serialVersionUID = 1L;
        Student(String username, String name, String passwordHash) {
            super(username, name, passwordHash, Role.STUDENT);
        }
    }

    static class Course implements Serializable {
        private static final long serialVersionUID = 1L;
        String courseCode, courseName;
        Set<String> enrolledStudents = new HashSet<>();

        public Course(String code, String name) {
            this.courseCode = code;
            this.courseName = name;
        }

        public void enrollStudent(String username) {
            enrolledStudents.add(username);
        }

        public boolean isStudentEnrolled(String username) {
            return enrolledStudents.contains(username);
        }
    }

    static abstract class Question implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, questionText;
        int marks;

        public Question(String id, String text, int marks) {
            this.id = id;
            this.questionText = text;
            this.marks = marks;
        }

        public abstract List<String> getOptions();
        public abstract int gradeAnswer(String answer);

        public String getId() { return id; }
        public String getText() { return questionText; }
        public int getMarks() { return marks; }
    }

    static class MCQQuestion extends Question {
        private static final long serialVersionUID = 1L;
        List<String> options;
        int correctIndex;

        public MCQQuestion(String id, String text, int marks, List<String> options, int correctIndex) {
            super(id, text, marks);
            this.options = options;
            this.correctIndex = correctIndex;
        }

        @Override
        public List<String> getOptions() { return options; }

        @Override
        public int gradeAnswer(String answer) {
            try {
                int ans = Integer.parseInt(answer);
                return ans == correctIndex ? marks : 0;
            } catch(Exception e) {
                return 0;
            }
        }
    }

    static class TrueFalseQuestion extends Question {
        private static final long serialVersionUID = 1L;
        boolean correctAnswer;

        public TrueFalseQuestion(String id, String text, int marks, boolean correctAnswer) {
            super(id, text, marks);
            this.correctAnswer = correctAnswer;
        }

        @Override
        public List<String> getOptions() {
            return Arrays.asList("True", "False");
        }

        @Override
        public int gradeAnswer(String answer) {
            if(answer == null) return 0;
            return (answer.equalsIgnoreCase("true") == correctAnswer) ? marks : 0;
        }
    }

    static class ShortAnswerQuestion extends Question {
        private static final long serialVersionUID = 1L;
        List<String> keywords;

        public ShortAnswerQuestion(String id, String text, int marks, List<String> keywords) {
            super(id, text, marks);
            this.keywords = keywords;
        }

        @Override
        public List<String> getOptions() { return null; }

        @Override
        public int gradeAnswer(String answer) {
            if(answer == null) return 0;
            int found = 0;
            String lAnswer = answer.toLowerCase();
            for(String kw : keywords)
                if(lAnswer.contains(kw.toLowerCase())) found++;
            return marks * found / keywords.size();
        }
    }

    static class Quiz implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, courseCode;
        Date startTime;
        int durationMinutes;
        List<Question> questions;

        public Quiz(String id, String courseCode, Date startTime, int durationMinutes, List<Question> questions) {
            this.id = id;
            this.courseCode = courseCode;
            this.startTime = startTime;
            this.durationMinutes = durationMinutes;
            this.questions = questions;
        }

        public boolean isActive(Date now) {
            long start = startTime.getTime();
            long end = start + durationMinutes * 60 * 1000L;
            return now.getTime() >= start && now.getTime() <= end;
        }
    }

    static class Result implements Serializable {
        private static final long serialVersionUID = 1L;
        String quizId, studentUsername;
        Map<String, String> answers = new HashMap<>();
        int marksObtained = 0;

        public Result(String quizId, String studentUsername) {
            this.quizId = quizId;
            this.studentUsername = studentUsername;
        }
    }

    // Data manager handles serialization and data access
    static class DataManager {
        List<User> users = new ArrayList<>();
        List<Course> courses = new ArrayList<>();
        List<Question> questions = new ArrayList<>();
        List<Quiz> quizzes = new ArrayList<>();
        List<Result> results = new ArrayList<>();

        final String FILE_NAME = "examdata.bin";

        void load() {
            File f = new File(FILE_NAME);
            if (!f.exists()) return;
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                users = (List<User>) ois.readObject();
                courses = (List<Course>) ois.readObject();
                questions = (List<Question>) ois.readObject();
                quizzes = (List<Quiz>) ois.readObject();
                results = (List<Result>) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        void save() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
                oos.writeObject(users);
                oos.writeObject(courses);
                oos.writeObject(questions);
                oos.writeObject(quizzes);
                oos.writeObject(results);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        boolean usernameExists(String username) {
            return users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
        }
        User getUserByUsername(String username) {
            return users.stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst().orElse(null);
        }

        void addUser(User u) {
            users.add(u);
            save();
        }
        boolean courseCodeExists(String code) {
            return courses.stream().anyMatch(c -> c.courseCode.equalsIgnoreCase(code));
        }
        void addCourse(Course c) {
            courses.add(c);
            save();
        }
        Course getCourseByCode(String code) {
            return courses.stream().filter(c -> c.courseCode.equalsIgnoreCase(code)).findFirst().orElse(null);
        }
        void addQuestion(Question q) {
            questions.add(q);
            save();
        }
        List<Question> getQuestions() { return questions; }
        void addQuiz(Quiz q) {
            quizzes.add(q);
            save();
        }
        List<Quiz> getQuizzesByCourse(String courseCode) {
            List<Quiz> list = new ArrayList<>();
            for (Quiz q : quizzes) {
                if (q.courseCode.equalsIgnoreCase(courseCode))
                    list.add(q);
            }
            return list;
        }
        Quiz getQuizById(String id) {
            return quizzes.stream().filter(q -> q.id.equalsIgnoreCase(id)).findFirst().orElse(null);
        }
        void addResult(Result r) {
            results.add(r);
            save();
        }
        Result getResult(String quizId, String studentUsername) {
            return results.stream().filter(r -> r.quizId.equalsIgnoreCase(quizId) && r.studentUsername.equalsIgnoreCase(studentUsername)).findFirst().orElse(null);
        }
        List<Result> getResultsByQuiz(String quizId) {
            List<Result> res = new ArrayList<>();
            for (Result r : results) {
                if (r.quizId.equalsIgnoreCase(quizId))
                    res.add(r);
            }
            return res;
        }
    }

    static DataManager dm = new DataManager();

    // Utility hashing class
    static class Utils {
        static String hashPassword(String password) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for(byte b : bytes) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch(Exception e) { e.printStackTrace(); return null; }
        }
    }

    // GUI - Login frame
    static class LoginFrame extends JFrame {
        JTextField txtUsername = new JTextField(10);  // Increased field size
        JPasswordField txtPassword = new JPasswordField(10);  // Increased field size
        JComboBox<String> cmbRole = new JComboBox<>(new String[] {"Teacher","Student"});
        JButton btnLogin = new JButton("Login");
        JButton btnRegister = new JButton("Register");

        public LoginFrame() {
            dm.load();
            setTitle("Login");
            setSize(450, 250);  // Increased window size
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            JPanel p = new JPanel(new GridBagLayout());
            p.setBorder(new EmptyBorder(15,15,15,15));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6,6,6,6);
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx=0; gbc.gridy=0; p.add(new JLabel("Username:"), gbc);
            gbc.gridx=1; p.add(txtUsername, gbc);

            gbc.gridx=0; gbc.gridy=1; p.add(new JLabel("Password:"), gbc);
            gbc.gridx=1; p.add(txtPassword, gbc);

            gbc.gridx=0; gbc.gridy=2; p.add(new JLabel("Role:"), gbc);
            gbc.gridx=1; p.add(cmbRole, gbc);

            gbc.gridx=0; gbc.gridy=3; p.add(btnLogin, gbc);
            gbc.gridx=1; p.add(btnRegister, gbc);

            add(p);

            btnLogin.addActionListener(e -> login());
            btnRegister.addActionListener(e -> {
                dispose();
                new RegisterFrame();
            });

            setVisible(true);
        }

        void login() {
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());
            String role = (String)cmbRole.getSelectedItem();

            if(username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter username and password.");
                return;
            }

            User user = dm.getUserByUsername(username);
            if(user == null) {
                JOptionPane.showMessageDialog(this, "User not found.");
                return;
            }

            if(!user.getPasswordHash().equals(Utils.hashPassword(password))) {
                JOptionPane.showMessageDialog(this, "Incorrect password.");
                return;
            }

            if(!user.getRole().toString().equalsIgnoreCase(role)) {
                JOptionPane.showMessageDialog(this, "Role mismatch.");
                return;
            }

            JOptionPane.showMessageDialog(this, "Welcome " + user.getName());
            dispose();
            if(user.getRole() == User.Role.TEACHER)
                new TeacherWindow((Teacher)user);
            else
                new StudentWindow((Student)user);
        }
    }

    // GUI - Register Frame
    static class RegisterFrame extends JFrame {
        JTextField txtName = new JTextField(10);  // Increased field size
        JTextField txtUsername = new JTextField(10);  // Increased field size
        JPasswordField txtPassword = new JPasswordField(10);  // Increased field size
        JComboBox<String> cmbRole = new JComboBox<>(new String[]{"Teacher","Student"});
        JButton btnRegister = new JButton("Register");
        JButton btnBack = new JButton("Back");

        public RegisterFrame() {
            setTitle("Register");
            setSize(450, 300);  // Increased window size
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            JPanel p = new JPanel(new GridBagLayout());
            p.setBorder(new EmptyBorder(15,15,15,15));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6,6,6,6);
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx=0; gbc.gridy=0; p.add(new JLabel("Name:"), gbc);
            gbc.gridx=1; p.add(txtName, gbc);

            gbc.gridx=0; gbc.gridy=1; p.add(new JLabel("Username:"), gbc);
            gbc.gridx=1; p.add(txtUsername, gbc);

            gbc.gridx=0; gbc.gridy=2; p.add(new JLabel("Password:"), gbc);
            gbc.gridx=1; p.add(txtPassword, gbc);

            gbc.gridx=0; gbc.gridy=3; p.add(new JLabel("Role:"), gbc);
            gbc.gridx=1; p.add(cmbRole, gbc);

            gbc.gridy=4; gbc.gridx=0; p.add(btnRegister, gbc);
            gbc.gridx=1; p.add(btnBack, gbc);

            add(p);

            btnRegister.addActionListener(e -> register());
            btnBack.addActionListener(e -> {
                dispose();
                new LoginFrame();
            });

            setVisible(true);
        }

        void register() {
            String name = txtName.getText().trim();
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());
            String role = (String) cmbRole.getSelectedItem();

            if(name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields.");
                return;
            }
            if(dm.usernameExists(username)) {
                JOptionPane.showMessageDialog(this, "Username already taken.");
                return;
            }
            String hash = Utils.hashPassword(password);
            if(role.equalsIgnoreCase("Teacher"))
                dm.addUser(new Teacher(username, name, hash));
            else
                dm.addUser(new Student(username, name, hash));
            JOptionPane.showMessageDialog(this, "Registration successful. Please login.");
            dispose();
            new LoginFrame();
        }
    }

    // GUI - Teacher Dashboard
    static class TeacherWindow extends JFrame {
        Teacher teacher;
        DefaultListModel<String> courseListModel = new DefaultListModel<>();
        JList<String> courseList = new JList<>(courseListModel);
        JButton btnAddCourse = new JButton("Add Course");
        JButton btnManageCourse = new JButton("Manage Course");
        JButton btnLogout = new JButton("Logout");

        public TeacherWindow(Teacher teacher) {
            this.teacher = teacher;
            setTitle("Teacher Dashboard - " + teacher.getName());
            setSize(700, 400);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setBorder(new EmptyBorder(10,10,10,10));
            leftPanel.add(new JLabel("Courses:"), BorderLayout.NORTH);
            leftPanel.add(new JScrollPane(courseList), BorderLayout.CENTER);

            JPanel btnPanel = new JPanel();
            btnPanel.add(btnAddCourse);
            btnPanel.add(btnManageCourse);
            btnPanel.add(btnLogout);

            leftPanel.add(btnPanel, BorderLayout.SOUTH);
            add(leftPanel, BorderLayout.WEST);

            refreshCourseList();

            btnAddCourse.addActionListener(e -> addCourse());
            btnManageCourse.addActionListener(e -> manageCourse());
            btnLogout.addActionListener(e -> {
                dispose();
                new LoginFrame();
            });

            setVisible(true);
        }

        void refreshCourseList() {
            courseListModel.clear();
            for(Course c : dm.courses){
                courseListModel.addElement(c.courseCode + " - " + c.courseName);
            }
        }

        void addCourse(){
            JTextField codeField = new JTextField(15);
            JTextField nameField = new JTextField(15);
            Object[] message = {
                    "Course Code:", codeField,
                    "Course Name:", nameField
            };
            int option = JOptionPane.showConfirmDialog(this, message, "Add New Course", JOptionPane.OK_CANCEL_OPTION);
            if(option == JOptionPane.OK_OPTION){
                String code = codeField.getText().trim();
                String name = nameField.getText().trim();
                if(code.isEmpty() || name.isEmpty()){
                    JOptionPane.showMessageDialog(this, "Please fill all fields");
                    return;
                }
                if(dm.courseCodeExists(code)){
                    JOptionPane.showMessageDialog(this, "Course code already exists");
                    return;
                }
                dm.addCourse(new Course(code, name));
                refreshCourseList();
            }
        }

        void manageCourse() {
            String selected = courseList.getSelectedValue();
            if(selected == null){
                JOptionPane.showMessageDialog(this, "Please select a course");
                return;
            }
            String code = selected.split(" - ")[0];
            Course course = dm.getCourseByCode(code);
            if(course != null) new ManageCourseWindow(course);
        }
    }

    // GUI - Manage Course window
    static class ManageCourseWindow extends JFrame {
        Course course;
        DefaultListModel<String> questionsModel = new DefaultListModel<>();
        JList<String> questionsList = new JList<>(questionsModel);
        JButton btnAddQuestion = new JButton("Add Question");
        JButton btnCreateQuiz = new JButton("Create Quiz");
        JButton btnViewSubmissions = new JButton("View Submissions");
        JButton btnEnrollStudent = new JButton("Enroll Student");

        public ManageCourseWindow(Course course){
            this.course = course;
            setTitle("Manage Course: " + course.courseCode);
            setSize(700,400);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setBorder(new EmptyBorder(10,10,10,10));
            leftPanel.add(new JLabel("Questions"), BorderLayout.NORTH);
            leftPanel.add(new JScrollPane(questionsList), BorderLayout.CENTER);
            leftPanel.add(btnAddQuestion, BorderLayout.SOUTH);

            JPanel bottomPanel = new JPanel();
            bottomPanel.add(btnCreateQuiz);
            bottomPanel.add(btnViewSubmissions);
            bottomPanel.add(btnEnrollStudent);

            add(leftPanel, BorderLayout.CENTER);
            add(bottomPanel, BorderLayout.SOUTH);

            refreshQuestionList();

            btnAddQuestion.addActionListener(e -> addQuestion());
            btnCreateQuiz.addActionListener(e -> createQuiz());
            btnViewSubmissions.addActionListener(e -> viewSubmissions());
            btnEnrollStudent.addActionListener(e -> enrollStudent());

            setVisible(true);
        }

        void refreshQuestionList(){
            questionsModel.clear();
            for(Question q: dm.questions){
                questionsModel.addElement(q.id + ": " + q.questionText);
            }
        }

        void addQuestion() {
            String[] types = {"MCQ", "True/False", "Short Answer"};
            String type = (String) JOptionPane.showInputDialog(this, "Select question type:", "Question Type",
                    JOptionPane.PLAIN_MESSAGE, null, types, types[0]);
            if(type == null) return;

            String text = JOptionPane.showInputDialog(this, "Enter question text:");
            if(text == null || text.trim().isEmpty()) return;

            int marks;
            try{
                marks = Integer.parseInt(JOptionPane.showInputDialog(this, "Enter marks:"));
                if(marks <= 0) throw new Exception();
            } catch(Exception e){
                JOptionPane.showMessageDialog(this,"Invalid marks");
                return;
            }

            String id = UUID.randomUUID().toString().substring(0,6);

            if(type.equals("MCQ")){
                List<String> opts = new ArrayList<>();
                for(int i=1; i<=4; i++){
                    String opt = JOptionPane.showInputDialog(this, "Option " + i + ":");
                    if(opt == null || opt.trim().isEmpty()) return;
                    opts.add(opt);
                }
                int correctIndex;
                try{
                    correctIndex = Integer.parseInt(JOptionPane.showInputDialog(this, "Correct option (1-4):")) - 1;
                    if(correctIndex < 0 || correctIndex > 3) throw new Exception();
                } catch(Exception e){
                    JOptionPane.showMessageDialog(this, "Invalid correct option");
                    return;
                }
                dm.addQuestion(new MCQQuestion(id, text, marks, opts, correctIndex));
            } else if(type.equals("True/False")){
                int ans = JOptionPane.showConfirmDialog(this, "Is the correct answer 'True'?", "Correct Answer", JOptionPane.YES_NO_OPTION);
                dm.addQuestion(new TrueFalseQuestion(id, text, marks, ans == JOptionPane.YES_OPTION));
            } else {
                String keywordsStr = JOptionPane.showInputDialog(this, "Enter keywords, comma separated:");
                if(keywordsStr == null || keywordsStr.trim().isEmpty()) return;
                List<String> keywords = new ArrayList<>();
                for(String kw : keywordsStr.split(",")){
                    kw = kw.trim();
                    if(!kw.isEmpty()) keywords.add(kw);
                }
                if(keywords.isEmpty()){
                    JOptionPane.showMessageDialog(this, "No valid keywords entered.");
                    return;
                }
                dm.addQuestion(new ShortAnswerQuestion(id, text, marks, keywords));
            }
            refreshQuestionList();
            JOptionPane.showMessageDialog(this,"Question added!");
        }

        void createQuiz(){
            if(dm.questions.isEmpty()){
                JOptionPane.showMessageDialog(this,"Add questions first.");
                return;
            }
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
            JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd HH:mm");
            dateSpinner.setEditor(timeEditor);

            JTextField durField = new JTextField(5);

            Object[] message = {
                    "Select Quiz Start Date & Time:", dateSpinner,
                    "Duration (minutes):", durField
            };
            int res = JOptionPane.showConfirmDialog(this, message, "Create Quiz", JOptionPane.OK_CANCEL_OPTION);
            if(res == JOptionPane.OK_OPTION){
                Date start = (Date) dateSpinner.getValue();
                String durStr = durField.getText().trim();
                int duration;
                try{
                    duration = Integer.parseInt(durStr);
                    if(duration <= 0) throw new Exception();
                } catch(Exception e){
                    JOptionPane.showMessageDialog(this, "Invalid duration");
                    return;
                }
                // Use all questions to create quiz for simplicity
                List<Question> questions = new ArrayList<>(dm.questions);
                Quiz quiz = new Quiz(UUID.randomUUID().toString(), course.courseCode, start, duration, questions);
                dm.addQuiz(quiz);
                JOptionPane.showMessageDialog(this,"Quiz created for " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(start));
            }
        }

        void viewSubmissions() {
            List<Quiz> quizzes = dm.getQuizzesByCourse(course.courseCode);
            if(quizzes.isEmpty()){
                JOptionPane.showMessageDialog(this,"No quizzes found.");
                return;
            }
            String[] qIds = quizzes.stream().map(q->q.id.substring(0,6)).toArray(String[]::new);
            String selected = (String) JOptionPane.showInputDialog(this, "Choose quiz to view submissions:",
                    "View Submissions", JOptionPane.PLAIN_MESSAGE, null, qIds, qIds[0]);
            if(selected == null) return;
            Quiz quiz = null;
            for(Quiz q : quizzes)
                if(q.id.startsWith(selected)) quiz = q;
            if(quiz == null) return;
            List<Result> results = dm.getResultsByQuiz(quiz.id);
            if(results.isEmpty()){
                JOptionPane.showMessageDialog(this,"No submissions yet.");
                return;
            }
            DefaultTableModel model = new DefaultTableModel(new Object[]{"Student", "Marks"},0);
            for(Result r : results) model.addRow(new Object[]{r.studentUsername, r.marksObtained});
            JTable table = new JTable(model);
            JOptionPane.showMessageDialog(this, new JScrollPane(table), "Submissions", JOptionPane.INFORMATION_MESSAGE);
        }

        void enrollStudent() {
            String username = JOptionPane.showInputDialog(this, "Enter student username to enroll:");
            if(username == null || username.trim().isEmpty()) return;

            if(dm.getUserByUsername(username) == null) {
                JOptionPane.showMessageDialog(this, "Student not found.");
                return;
            }

            course.enrollStudent(username);
            dm.save();
            JOptionPane.showMessageDialog(this, "Student enrolled successfully.");
        }
    }

    static class StudentWindow extends JFrame {
        Student student;
        DefaultTableModel quizTableModel = new DefaultTableModel(new Object[]{"Quiz ID", "Course", "Start", "Duration", "Status"}, 0);
        JTable quizTable = new JTable(quizTableModel);

        JButton btnAttempt = new JButton("Attempt Quiz");
        JButton btnViewResult = new JButton("View Result");
        JButton btnLogout = new JButton("Logout");

        public StudentWindow(Student student) {
            this.student = student;
            setTitle("Student Dashboard - " + student.getName());
            setSize(750, 400);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout(10,10));
            add(new JLabel("Available Quizzes"), BorderLayout.NORTH);
            add(new JScrollPane(quizTable), BorderLayout.CENTER);
            JPanel btnPanel = new JPanel();
            btnPanel.add(btnAttempt);
            btnPanel.add(btnViewResult);
            btnPanel.add(btnLogout);
            add(btnPanel, BorderLayout.SOUTH);

            refreshQuizTable();

            btnAttempt.addActionListener(e -> attemptQuiz());
            btnViewResult.addActionListener(e -> viewResult());
            btnLogout.addActionListener(e -> {
                dispose();
                new LoginFrame();
            });

            quizTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
                boolean selected = quizTable.getSelectedRow() != -1;
                btnAttempt.setEnabled(selected);
                btnViewResult.setEnabled(selected);
            });

            setVisible(true);
        }

        void refreshQuizTable() {
            quizTableModel.setRowCount(0);
            long now = System.currentTimeMillis();
            for(Quiz q : dm.quizzes) {
                Course c = dm.getCourseByCode(q.courseCode);
                if(c != null && c.isStudentEnrolled(student.getUsername())) {
                    long start = q.startTime.getTime();
                    long end = start + q.durationMinutes * 60 * 1000L;
                    // Show upcoming and active quizzes
                    if (now <= end) {
                        String status = now < start ? "Upcoming" : "Active";
                        quizTableModel.addRow(new Object[]{
                                q.id.substring(0,6),
                                c.courseName,
                                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(q.startTime),
                                q.durationMinutes,
                                status
                        });
                    }
                }
            }
        }

        void attemptQuiz() {
            int selected = quizTable.getSelectedRow();
            if(selected == -1) return;
            String quizIdShort = (String)quizTableModel.getValueAt(selected, 0);
            Quiz quiz = getQuizByIdPrefix(quizIdShort);
            if(quiz == null) return;

            if(dm.getResult(quiz.id, student.getUsername()) != null) {
                JOptionPane.showMessageDialog(this,"Quiz already attempted.");
                return;
            }

            long now = System.currentTimeMillis();
            long start = quiz.startTime.getTime();
            long end = start + quiz.durationMinutes * 60 * 1000L;
            if(now < start) {
                JOptionPane.showMessageDialog(this,"Quiz not active yet.");
                return;
            }
            if(now > end) {
                JOptionPane.showMessageDialog(this,"Quiz time expired.");
                return;
            }
            new QuizAttemptFrame(student, quiz, this);
            setVisible(false);
        }

        void viewResult() {
            int selected = quizTable.getSelectedRow();
            if(selected == -1) return;
            String idShort = (String)quizTableModel.getValueAt(selected, 0);
            Quiz quiz = getQuizByIdPrefix(idShort);
            if(quiz == null) return;
            Result res = dm.getResult(quiz.id, student.getUsername());
            if(res == null) {
                JOptionPane.showMessageDialog(this,"Quiz not attempted yet.");
                return;
            }
            int totalMarks = 0;
            for(Question q : quiz.questions) totalMarks += q.marks;
            JOptionPane.showMessageDialog(this, "Your score: "+ res.marksObtained + "/" + totalMarks);
        }

        Quiz getQuizByIdPrefix(String prefix) {
            for(Quiz q : dm.quizzes) if(q.id.startsWith(prefix)) return q;
            return null;
        }
    }

    static class QuizAttemptFrame extends JFrame {
        Student student;
        Quiz quiz;
        Result result;
        int currentIndex;
        List<Question> questions;
        Map<String, String> answers = new HashMap<>();

        JLabel lblQuestion = new JLabel();
        JPanel panelOptions = new JPanel();
        JButton btnPrev = new JButton("Previous");
        JButton btnNext = new JButton("Next");
        JButton btnSubmit = new JButton("Submit");

        ButtonGroup bg = new ButtonGroup();  // Initialize here
        List<JRadioButton> optionButtons;
        JTextArea shortAnswerArea;
        StudentWindow parent;

        public QuizAttemptFrame(Student student, Quiz quiz, StudentWindow parent) {
            super("Quiz: "+ quiz.courseCode);
            this.student = student;
            this.quiz = quiz;
            this.questions = quiz.questions;
            this.parent = parent;
            this.result = new Result(quiz.id, student.getUsername());

            setSize(600,400);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout(10,10));

            lblQuestion.setFont(new Font("Arial", Font.BOLD, 16));
            add(lblQuestion, BorderLayout.NORTH);

            panelOptions.setLayout(new BoxLayout(panelOptions, BoxLayout.Y_AXIS));
            add(new JScrollPane(panelOptions), BorderLayout.CENTER);

            JPanel btnPanel = new JPanel();
            btnPanel.add(btnPrev);
            btnPanel.add(btnNext);
            btnPanel.add(btnSubmit);
            add(btnPanel, BorderLayout.SOUTH);

            btnPrev.addActionListener(e -> showQuestion(currentIndex - 1));
            btnNext.addActionListener(e -> showQuestion(currentIndex + 1));
            btnSubmit.addActionListener(e -> submit());

            showQuestion(0);
            setVisible(true);
        }

        void showQuestion(int idx) {
            if(idx < 0 || idx >= questions.size()) return;
            saveAnswer();
            currentIndex = idx;
            Question q = questions.get(idx);
            lblQuestion.setText("Q"+ (idx+1) + ": "+ q.questionText);
            panelOptions.removeAll();

            List<String> opts = q.getOptions();
            bg.clearSelection(); // Clear existing buttons
            optionButtons = new ArrayList<>();
            shortAnswerArea = null;

            if(opts != null){
                for(int i=0; i<opts.size(); i++){
                    JRadioButton rb = new JRadioButton(opts.get(i));
                    rb.setActionCommand(String.valueOf(i));
                    bg.add(rb);
                    optionButtons.add(rb);
                    panelOptions.add(rb);
                }
                String saved = answers.get(q.getId());
                if(saved != null){
                    for(JRadioButton b : optionButtons) if(b.getActionCommand().equals(saved)) b.setSelected(true);
                }
            } else {
                shortAnswerArea = new JTextArea(5, 50);
                if(answers.get(q.getId()) != null) shortAnswerArea.setText(answers.get(q.getId()));
                panelOptions.add(new JScrollPane(shortAnswerArea));
                shortAnswerArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { saveAnswer(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { saveAnswer(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { saveAnswer(); }
                });
            }

            panelOptions.revalidate();
            panelOptions.repaint();

            btnPrev.setEnabled(idx > 0);
            btnNext.setEnabled(idx < questions.size()-1);
        }

        void saveAnswer(){
            if(currentIndex < 0 || currentIndex >= questions.size()) return;
            Question q = questions.get(currentIndex);
            if(q.getOptions() != null){
                if(bg.getSelection() != null)
                    answers.put(q.getId(), bg.getSelection().getActionCommand());
            } else {
                if(shortAnswerArea != null)
                    answers.put(q.getId(), shortAnswerArea.getText());
            }
        }

        void submit(){
            saveAnswer();
            int total = questions.stream().mapToInt(q -> q.marks).sum();
            int obtained = 0;
            for(Question q : questions)
                obtained += q.gradeAnswer(answers.getOrDefault(q.getId(), ""));
            result.marksObtained = obtained;
            result.answers = new HashMap<>(answers);
            dm.addResult(result);
            JOptionPane.showMessageDialog(this, "Quiz submitted!\nYour Score: " + obtained + "/" + total);
            parent.refreshQuizTable();
            parent.setVisible(true);
            dispose();
        }
    }}