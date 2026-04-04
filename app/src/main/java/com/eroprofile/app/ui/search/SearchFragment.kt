package com.eroprofile.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eroprofile.app.adapters.VideoAdapter
import com.eroprofile.app.databinding.FragmentSearchBinding
import com.eroprofile.app.ui.video.VideoPlayerActivity

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchInput()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupSearchInput() {
        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString() ?: ""
            if (query.length >= 2) {
                viewModel.search(query)
            } else if (query.isEmpty()) {
                viewModel.search("")
            }
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchInput.text?.toString() ?: ""
                viewModel.search(query)
                hideKeyboard()
                true
            } else false
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { video ->
            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URL, video.url)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title)
            }
            startActivity(intent)
        }

        binding.recyclerSearchResults.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = videoAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val lm = rv.layoutManager as GridLayoutManager
                    if (lm.itemCount <= lm.findLastVisibleItemPosition() + 4) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun observeViewModel() {
        viewModel.results.observe(viewLifecycleOwner) { results ->
            videoAdapter.submitList(results)
            binding.recyclerSearchResults.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
            if (results.isEmpty() && binding.searchInput.text?.toString()?.isNotEmpty() == true) {
                binding.emptyText.text = getString(com.eroprofile.app.R.string.no_results)
                binding.emptyView.visibility = View.VISIBLE
            } else if (results.isEmpty()) {
                binding.emptyText.text = getString(com.eroprofile.app.R.string.search_hint)
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.emptyView.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading && videoAdapter.itemCount == 0) View.VISIBLE else View.GONE
        }
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
