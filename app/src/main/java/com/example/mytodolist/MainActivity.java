package com.example.mytodolist;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private EditText taskInput;
    private Button addButton;
    private RecyclerView taskRecyclerView;
    private ArrayList<Task> tasks;
    private TaskAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link UI elements
        taskInput = findViewById(R.id.taskInput);
        addButton = findViewById(R.id.addButton);
        taskRecyclerView = findViewById(R.id.taskRecyclerView);

        // Initialize task list and adapter
        tasks = new ArrayList<>();
        adapter = new TaskAdapter(tasks);
        taskRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        taskRecyclerView.setAdapter(adapter);

        // Add task button listener
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String taskText = taskInput.getText().toString();
                if (!taskText.isEmpty()) {
                    tasks.add(new Task(taskText));
                    adapter.notifyItemInserted(tasks.size() - 1);
                    taskInput.setText("");
                }
            }
        });
    }
}