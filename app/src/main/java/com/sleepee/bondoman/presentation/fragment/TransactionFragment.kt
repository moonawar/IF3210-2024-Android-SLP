package com.sleepee.bondoman.presentation.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.sleepee.bondoman.data.model.TransactionDao
import com.sleepee.bondoman.data.model.TransactionDatabase
import com.sleepee.bondoman.databinding.FragmentTransactionBinding
import com.sleepee.bondoman.presentation.activity.AddTransactionActivity
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LOCATION
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LATITUDE
import com.sleepee.bondoman.presentation.activity.INTENT_EXTRA_LONGITUDE
import com.sleepee.bondoman.presentation.adapter.TransactionsAdapter
import java.util.Locale
import kotlin.concurrent.thread

const val REQUEST_CODE = 100


@Suppress("DEPRECATION")
class TransactionFragment : Fragment() {

    private lateinit var binding: FragmentTransactionBinding
    private var address = "Institut Teknologi Bandung"
    private var currentLatitude : Double ?= null
    private var currentLongitude : Double ?= null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val transactionDao: TransactionDao by lazy {
        TransactionDatabase.getDatabase(requireContext()).getTransactionDao()
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

        fetchAllTransactions()

        // Initialize FusedLocationProviderClient to detect current user's location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        // Happens when user presses on the plus button
        binding.fab.setOnClickListener {
            getLastLocation()

        }
    }

    fun fetchAllTransactions() {
        thread {
            val transactions = transactionDao.getAllTransactions()
            requireActivity().runOnUiThread {
                binding.recyclerView.adapter = TransactionsAdapter(
                    transactions = transactions
                )
            }
        }
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
            fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_LOW_POWER, object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener) = CancellationTokenSource().token
                override fun isCancellationRequested() = false
            })
                .addOnCompleteListener(requireActivity()) { task ->
                    val location: Location? = task.result
                    // do reverse geocoding using geocoder based on the latitude + longitude from FusedLocationProviderClient
                    if (location != null) {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null) {
                            address = addresses[0].getAddressLine(0)
                        }
                    }
                    // switch to AddTransactionActivity, while passing the address and the coordinates data
                    val intent = Intent(activity, AddTransactionActivity::class.java)
                    intent.putExtra(INTENT_EXTRA_LOCATION, address)
                    intent.putExtra(INTENT_EXTRA_LATITUDE, currentLatitude)
                    intent.putExtra(INTENT_EXTRA_LONGITUDE, currentLongitude)
                    startActivity(intent)
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


}