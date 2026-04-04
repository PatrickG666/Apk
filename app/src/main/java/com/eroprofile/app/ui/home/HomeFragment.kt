package com.eroprofile.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eroprofile.app.R
import com.eroprofile.app.adapters.VideoAdapter
import com.eroprofile.app.data.scraper.EroProfileScraper
import com.eroprofile.app.databinding.FragmentHomeBinding
import com.eroprofile.app.ui.video.VideoPlayerActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChips()
        setupSwipeRefresh()
        observeViewModel()
        handleCategoryArguments()
    }

    private fun handleCategoryArguments() {
        val categoryUrl = arguments?.getString("categoryUrl")
        val categoryName = arguments?.getString("categoryName")
        if (!categoryUrl.isNullOrEmpty() && !categoryName.isNullOrEmpty()) {
            // Hide sort chips when browsing a category
            binding.sortChipsScroll.visibility = View.GONE
            viewModel.loadCategory(categoryUrl, categoryName)
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

        binding.recyclerVideos.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = videoAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if (totalItemCount <= lastVisibleItem + 4) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupChips() {
        val chips = mapOf(
            binding.chipRecent to EroProfileScraper.SORT_RECENT,
            binding.chipPopular to EroProfileScraper.SORT_POPULAR,
            binding.chipTopRated to EroProfileScraper.SORT_TOP_RATED
        )

        chips.forEach { (chip, sort) ->
            chip.setOnClickListener {
                chips.keys.forEach { c ->
                    c.setBackgroundResource(R.drawable.bg_chip)
                    c.setTextColor(ContextCompat.getColor(requireContext(), R.color.ep_text_secondary))
                }
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.ep_text_primary))
                viewModel.loadVideos(sort)
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.ep_orange)
            )
            setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.ep_background)
            )
            setOnRefreshListener {
                viewModel.refresh()
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }
    }

    private fun observeViewModel() {
        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            videoAdapter.submitList(videos)
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (videoAdapter.itemCount == 0) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.swipeRefresh.isRefreshing = false
            if (error != null && videoAdapter.itemCount == 0) {
                binding.errorView.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
            } else {
                binding.errorView.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
