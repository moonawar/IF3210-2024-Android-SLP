package com.sleepee.bondoman.presentation.fragment

import android.Manifest
import android.R.attr.name
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.gson.Gson
import com.sleepee.bondoman.R
import com.sleepee.bondoman.data.model.Transaction
import com.sleepee.bondoman.data.model.TransactionDao
import com.sleepee.bondoman.data.model.TransactionDatabase
import com.sleepee.bondoman.databinding.FragmentTransactionBinding
import com.sleepee.bondoman.presentation.activity.AddTransactionActivity
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_AMOUNT
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_CATEGORY
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_DATE
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_ID
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_LOCATION
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_LOCATION_LINK
import com.sleepee.bondoman.presentation.activity.EDIT_TRANSACTION_TITLE
import com.sleepee.bondoman.presentation.activity.EditTransactionActivity
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LATITUDE
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LOCATION
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LONGITUDE
import com.sleepee.bondoman.presentation.activity.MainActivity
import com.sleepee.bondoman.presentation.adapter.TransactionsAdapter
import com.sleepee.bondoman.presentation.viewmodel.TransactionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.concurrent.thread


const val REQUEST_CODE = 100

@Suppress("DEPRECATION")
class TransactionFragment : Fragment(), TransactionsAdapter.LocationButtonListener {

    private lateinit var binding: FragmentTransactionBinding
    var address = "Institut Teknologi Bandung"
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mTransactionViewModel: TransactionViewModel
    private lateinit var database: TransactionDatabase
    private val transactionDao: TransactionDao by lazy {
        database.getTransactionDao()
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTransactionBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        database = TransactionDatabase.getDatabase(requireContext())

        val coroutineScope = CoroutineScope(Dispatchers.Main)

        coroutineScope.launch {
            fetchAllTransactions()
        }
        coroutineScope.cancel()

        runOnItemClicked()

        getLastLocation()

        // Initialize FusedLocationProviderClient to detect current user's location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Happens when user presses on the plus button
        binding.fab.setOnClickListener {
            val intent = Intent(activity, AddTransactionActivity::class.java)
            intent.putExtra(INTENT_EXTRA_LOCATION, address)
            Log.d("address", "address: $address")
            intent.putExtra(INTENT_EXTRA_LATITUDE, currentLatitude)
            intent.putExtra(INTENT_EXTRA_LONGITUDE, currentLongitude)
            startActivity(intent)
        }

        // Happens when the user presses on the trash button
        binding.fabClearTransactions.setOnClickListener {
            createConfirmationDialog()
        }


    }

    override fun onPause() {
        super.onPause()

        val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
        val myEdit = sharedPreferences.edit()

        // write all the data entered by the user in SharedPreference and apply
        myEdit.putString("location", address)
        myEdit.apply()

        Log.d("address", "address: $address")
    }


    private fun runOnItemClicked() {
//            val transactions = transactionDao.getAllTransactions()
        val adapter = TransactionsAdapter(listener = this)

        mTransactionViewModel = ViewModelProvider(this).get(TransactionViewModel::class.java)
        mTransactionViewModel.readAllData.observe(viewLifecycleOwner, Observer { transaction ->
            adapter.setData(transaction)

            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            val gson = Gson()
            val json = gson.toJson(adapter.transactions)

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putString("transactions", json)
            myEdit.apply()
        })
        mTransactionViewModel.pemasukanCount.observe(viewLifecycleOwner, Observer { count ->
            adapter.setPemasukanCount(count)
            Log.d("transactionsCount", "Pemasukan: ${adapter.pemasukan}")
            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putInt("pemasukan", adapter.pemasukan)
            myEdit.apply()

            Log.d("address", "address: $address")
        })
        mTransactionViewModel.pengeluaranCount.observe(viewLifecycleOwner, Observer { count ->
            adapter.setPengeluaranCount(count)
            Log.d("transactionsCount", "Pengeluaran: ${adapter.pengeluaran}")
            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putInt("pengeluaran", adapter.pengeluaran)
            myEdit.apply()
        })
        requireActivity().runOnUiThread {

            binding.recyclerView.adapter = adapter



            adapter.setOnClickListener(object :
                TransactionsAdapter.OnClickListener {
                override fun onClick(position: Int, model: Transaction) {
                    val intent = Intent(activity, EditTransactionActivity::class.java)
                    val extras = transactionToBundle(model)
                    intent.putExtras(extras)
                    startActivity(intent)
                }
            }
            )

        }

    }

    override fun onLocationButtonPressed(transaction: Transaction) {
        val gmmIntentUri = Uri.parse(transaction.locationLink)
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        startActivity(mapIntent)
    }

    private fun transactionToBundle(model: Transaction): Bundle {
        val extras = Bundle()
        extras.putInt(EDIT_TRANSACTION_ID, model.transactionId)
        extras.putString(EDIT_TRANSACTION_TITLE, model.title)
        extras.putInt(EDIT_TRANSACTION_AMOUNT, model.amount)
        extras.putString(EDIT_TRANSACTION_CATEGORY, model.category)
        extras.putString(EDIT_TRANSACTION_LOCATION, model.location)
        extras.putString(EDIT_TRANSACTION_DATE, model.date)
        extras.putString(EDIT_TRANSACTION_LOCATION_LINK, model.locationLink)
        return extras
    }

    private fun fetchAllTransactions() {
        val adapter = TransactionsAdapter(listener = this)

        mTransactionViewModel = ViewModelProvider(this).get(TransactionViewModel::class.java)
        mTransactionViewModel.readAllData.observe(viewLifecycleOwner, Observer { transaction ->
            adapter.setData(transaction)

            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            val gson = Gson()
            val json = gson.toJson(adapter.transactions)

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putString("transactions", json)
            myEdit.apply()
        })
        mTransactionViewModel.pemasukanCount.observe(viewLifecycleOwner, Observer { count ->
            adapter.setPemasukanCount(count)
            Log.d("transactionsCount", "Pemasukan: ${adapter.pemasukan}")

            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putInt("pemasukan", adapter.pemasukan)
            myEdit.apply()
        })
        mTransactionViewModel.pengeluaranCount.observe(viewLifecycleOwner, Observer { count ->
            adapter.setPengeluaranCount(count)
            Log.d("transactionsCount", "Pengeluaran: ${adapter.pengeluaran}")

            val sharedPreferences = requireActivity().getSharedPreferences("MySharedPref", MODE_PRIVATE)
            val myEdit = sharedPreferences.edit()

            // write all the data entered by the user in SharedPreference and apply
            myEdit.putInt("pengeluaran", adapter.pengeluaran)
            myEdit.apply()
        })
        Log.d("transactionsCount", "Transactions: ${adapter.itemCount}")




    }


    // Finding out if the phone has the location enabled on the settings.
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun getLastLocation() {

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // if the device is not granted the permissions to use the user's location, show a permission dialog for the user to share their location
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQUEST_CODE
            )
            return
        }

        // If location in the settings is enabled, add transactions.
        if (isLocationEnabled()) {
            // detect current location of the phone using FusedLocationProviderClient
            fusedLocationClient.getCurrentLocation(
                LocationRequest.PRIORITY_LOW_POWER,
                object : CancellationToken() {
                    override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                        CancellationTokenSource().token

                    override fun isCancellationRequested() = false
                })
                .addOnCompleteListener(requireActivity()) { task ->
                    val location: Location? = task.result
                    // do reverse geocoding using geocoder based on the latitude + longitude from FusedLocationProviderClient
                    if (location != null) {
                        val geocoder = context?.let { Geocoder(it, Locale.getDefault()) }
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        val addresses =
                            geocoder?.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null) {
                            address = addresses[0].getAddressLine(0)
                        }
                    }
                    // switch to AddTransactionActivity, while passing the address and the coordinates data
                }
        } else {
            // Location is not enabled yet --> Transaction activity cannot be activated, redirected to the location settings.
            Toast.makeText(requireContext(), "Please turn on location", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }

    //Request permission, if allowed then getLastLocation()
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please provide the required permission",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun createConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear all transactions")
            .setMessage("Are you sure you want to clear all transactions?")
            .setPositiveButton("Yes") { _, _ ->
                thread {
                    transactionDao.deleteAllTransactions()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }


}