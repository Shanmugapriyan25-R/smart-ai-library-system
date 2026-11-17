-- Smart AI Library Management System - Flyway Migration V2__seed_initial_data.sql

-- 1. Insert Initial Categories
INSERT INTO categories (name, code, description) VALUES
('Computer Science & AI', 'CS-AI', 'Artificial Intelligence, Machine Learning, Data Science, Software Engineering, and Cloud Computing'),
('Information Technology', 'IT-SYS', 'Networks, Cybersecurity, Database Management, and Enterprise Systems'),
('Electronics & Electrical', 'ECE-EEE', 'Embedded Systems, IoT, Robotics, Signal Processing, and VLSI'),
('Mechanical & Robotics', 'MECH-ROB', 'Thermodynamics, Fluid Mechanics, Robotics, and Automated Manufacturing'),
('Mathematics & Statistics', 'MATH-STAT', 'Applied Mathematics, Linear Algebra, Probability, Statistics, and Discrete Math'),
('Literature & Humanities', 'LIT-HUM', 'Philosophy, World History, Classic Literature, and Academic Writing');

-- 2. Insert Initial Books
INSERT INTO books (isbn, title, author, category_id, publisher, publication_year, edition, total_copies, available_copies, location_shelf, description) VALUES
('978-0134685991', 'Effective Java', 'Joshua Bloch', 1, 'Addison-Wesley', 2018, '3rd Edition', 5, 4, 'Shelf CS-01', 'Definitive guide to Java best practices, covering design patterns, concurrency, generics, and functional programming.'),
('978-0262033848', 'Introduction to Algorithms', 'Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein', 1, 'MIT Press', 2009, '3rd Edition', 6, 5, 'Shelf CS-02', 'Comprehensive textbook on algorithms, data structures, dynamic programming, graph algorithms, and computational complexity.'),
('978-0132350884', 'Clean Code: A Handbook of Agile Software Craftsmanship', 'Robert C. Martin', 1, 'Prentice Hall', 2008, '1st Edition', 4, 3, 'Shelf CS-03', 'A guide to writing clean, maintainable, readable code and refactoring principles for professional software engineers.'),
('978-1491957660', 'Designing Data-Intensive Applications', 'Martin Kleppmann', 1, 'O Reilly Media', 2017, '1st Edition', 5, 4, 'Shelf CS-04', 'In-depth architecture analysis of storage engines, distributed consensus, stream processing, replication, and partitioning.'),
('978-0262035613', 'Deep Learning', 'Ian Goodfellow, Yoshua Bengio, Aaron Courville', 1, 'MIT Press', 2016, '1st Edition', 4, 3, 'Shelf AI-01', 'Mathematical foundation and algorithms for deep neural networks, convolutional networks, transformers, and generative models.'),
('978-1449369415', 'Artificial Intelligence: A Modern Approach', 'Stuart Russell, Peter Norvig', 1, 'Pearson', 2020, '4th Edition', 5, 4, 'Shelf AI-02', 'The gold standard reference textbook in artificial intelligence, search algorithms, logic, planning, and multi-agent systems.'),
('978-0134092669', 'Operating System Concepts', 'Abraham Silberschatz, Peter B. Galvin, Greg Gagne', 2, 'Wiley', 2018, '10th Edition', 4, 4, 'Shelf IT-01', 'Core principles of OS processes, memory management, file systems, synchronization, virtualization, and storage subsystems.'),
('978-0133594140', 'Computer Networking: A Top-Down Approach', 'James F. Kurose, Keith W. Ross', 2, 'Pearson', 2021, '8th Edition', 4, 4, 'Shelf IT-02', 'Modern networking fundamentals, application layer protocols, transport layer TCP/UDP, routing algorithms, and wireless networking.'),
('978-0131103627', 'The C Programming Language', 'Brian W. Kernighan, Dennis M. Ritchie', 1, 'Prentice Hall', 1988, '2nd Edition', 3, 3, 'Shelf CS-05', 'The classic authoritative guide to the C programming language by its designers.'),
('978-0201616224', 'The Pragmatic Programmer: Your Journey To Mastery', 'David Thomas, Andrew Hunt', 1, 'Addison-Wesley', 2019, '20th Anniversary Edition', 4, 4, 'Shelf CS-06', 'Essential career wisdom, engineering philosophy, automation, testing strategies, and software craft.');

-- 3. Insert Initial Library Seats across Floors and Sections
INSERT INTO library_seats (seat_number, floor, section, has_power_outlet, is_accessible, status) VALUES
('S-101', '1st Floor', 'Quiet Study Zone', TRUE, TRUE, 'ACTIVE'),
('S-102', '1st Floor', 'Quiet Study Zone', TRUE, TRUE, 'ACTIVE'),
('S-103', '1st Floor', 'Quiet Study Zone', TRUE, FALSE, 'ACTIVE'),
('S-104', '1st Floor', 'Window View Zone', TRUE, TRUE, 'ACTIVE'),
('S-105', '1st Floor', 'Window View Zone', FALSE, TRUE, 'ACTIVE'),
('S-201', '2nd Floor', 'Collaborative Zone', TRUE, TRUE, 'ACTIVE'),
('S-202', '2nd Floor', 'Collaborative Zone', TRUE, TRUE, 'ACTIVE'),
('S-203', '2nd Floor', 'Research Pods', TRUE, TRUE, 'ACTIVE'),
('S-204', '2nd Floor', 'Research Pods', TRUE, FALSE, 'ACTIVE'),
('S-205', '2nd Floor', 'Silent Carrels', FALSE, TRUE, 'ACTIVE'),
('S-301', '3rd Floor', 'Digital Media Lab', TRUE, TRUE, 'ACTIVE'),
('S-302', '3rd Floor', 'Digital Media Lab', TRUE, TRUE, 'ACTIVE');

-- 4. Insert System Settings
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('max_borrow_limit', '3', 'Maximum number of active book loans permitted per student account'),
('borrow_duration_days', '14', 'Standard loan period in days before a borrowed book is marked overdue'),
('daily_fine_rate', '1.00', 'Fine amount in currency units charged per day overdue'),
('allow_student_reservations', 'true', 'Global switch permitting students to book library study carrels'),
('ai_assistant_enabled', 'true', 'Global switch enabling AI smart assistant and RAG search queries'),
('semantic_search_threshold', '0.65', 'Cosine similarity cutoff score for semantic RAG document retrieval');
