package com.puzzlehub.sudoku.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.game.databinding.FragmentHistoryBinding;
import com.puzzlehub.sudoku.game.databinding.SudokuItemHistoryBinding;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.utils.Format;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class HistoryFragment extends Fragment {
    public HistoryFragment() { super(R.layout.fragment_history); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentHistoryBinding binding = FragmentHistoryBinding.bind(view);
        Adapter adapter = new Adapter();
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        SudokuRuntime application = SudokuRuntime.get(requireContext());
        HistoryViewModel model = new ViewModelProvider(this, new AppViewModelFactory(application))
                .get(HistoryViewModel.class);
        model.getHistory().observe(getViewLifecycleOwner(), records -> {
            adapter.submitList(records);
            if (records.isEmpty()) {
                binding.empty.setVisibility(View.VISIBLE);
            } else {
                binding.empty.setVisibility(View.GONE);
            }
        });
    }

    private static final class Adapter extends ListAdapter<GameHistoryEntity, Holder> {
        Adapter() {
            super(new DiffUtil.ItemCallback<>() {
                @Override public boolean areItemsTheSame(GameHistoryEntity a, GameHistoryEntity b) {
                    return a.id.equals(b.id);
                }

                @Override public boolean areContentsTheSame(GameHistoryEntity a, GameHistoryEntity b) {
                    return a.result.equals(b.result) && a.score == b.score && a.duration == b.duration
                            && a.date == b.date && a.mistakes == b.mistakes && a.hints == b.hints
                            && a.mode.equals(b.mode) && a.difficulty.equals(b.difficulty);
                }
            });
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(SudokuItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            GameHistoryEntity game = getItem(position);
            SudokuItemHistoryBinding binding = holder.binding;
            binding.title.setText(Difficulty.valueOf(game.difficulty).getLabel() + " · " + Format.modeLabel(game.mode));
            String date = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(new Date(game.date));
            binding.detail.setText(date + "\n" + Format.duration(game.duration) + " · "
                    + game.mistakes + " lỗi · " + game.hints + " gợi ý");
            binding.result.setText(Format.resultLabel(game.result) + " · " + game.score + " điểm");
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final SudokuItemHistoryBinding binding;
        Holder(SudokuItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
