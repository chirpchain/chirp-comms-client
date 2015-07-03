package com.cherrydev.chirpusbdriverdemo;

import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.cherrydev.usbaudiodriver.AudioPlayback;
import com.cherrydev.usbaudiodriver.AudioWriteCallback;
import com.cherrydev.usbaudiodriver.KnownDevices;
import com.cherrydev.usbaudiodriver.UsbAudioDevice;
import com.cherrydev.usbaudiodriver.UsbAudioDeviceDescriptor;
import com.cherrydev.usbaudiodriver.UsbAudioDriver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends ActionBarActivity {

    private boolean gotAudio;
    private Handler handler = new Handler();
    private Map<UsbAudioDevice, Integer> statusMap = new HashMap<>();
    private UsbAudioDevice monitorDevice;
    private ArrayAdapter<UsbAudioDevice> deviceListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UsbDevice[] rawUsbDevices = UsbAudioDriver.getAttachedAudioDevices();
        ArrayList<UsbAudioDevice> audioDevices = new ArrayList<>();
        for (UsbDevice rawUsbDevice : rawUsbDevices) {
            Log.d("DeviceList", rawUsbDevice.getDeviceName());
            UsbAudioDeviceDescriptor deviceDescriptor = KnownDevices.findByVenderProduct(rawUsbDevice.getVendorId(), rawUsbDevice.getProductId());
            if (deviceDescriptor == null) {
                Log.d("DeviceList", "Found unknown device " + rawUsbDevice.getDeviceName());
            }
            final UsbAudioDevice audioDevice = new UsbAudioDevice(deviceDescriptor, rawUsbDevice.getDeviceName(), new AudioWriteCallback() {
                @Override
                public void write(UsbAudioDevice audioDevice, byte[] data, int length) {
                    statusMap.put(audioDevice, 2);
                    if (! gotAudio) {
                        gotAudio = true;
                        Log.d("AudioRecieve", String.format("Got %d audios!", length));
                    }
                    if (audioDevice == monitorDevice) {
                        AudioPlayback.write(data, length);
                    }
                }
            });
            audioDevices.add(audioDevice);
            statusMap.put(audioDevice, 0);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    statusMap.put(audioDevice, statusMap.get(audioDevice) - 1);
                    handler.postDelayed(this, 1000);
                }
            }, 1000);
        }
        deviceListAdapter = new ArrayAdapter<UsbAudioDevice>(this, R.layout.usb_device_item, audioDevices.toArray(new UsbAudioDevice[0])) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final UsbAudioDevice device = getItem(position);
                if (convertView == null) {
                    convertView = MainActivity.this.getLayoutInflater().inflate(R.layout.usb_device_item, parent, false);
                }
                ((TextView)convertView.findViewById(R.id.usb_device_name)).setText("Name: " + device.getDescriptor().name);
                ((TextView)convertView.findViewById(R.id.usb_device_port)).setText("Port: " + device.getPort());
                ((TextView)convertView.findViewById(R.id.usb_device_volume)).setText("Vol: " + (int) device.getVolume());
                ((Switch)convertView.findViewById(R.id.enable_switch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            device.open();
                            device.beginReadAudio();
                        } else {
                            device.close();
                        }
                    }
                });
                CheckBox monitorCheck = ((CheckBox)convertView.findViewById(R.id.monitor_switch));
                monitorCheck.setChecked(device == monitorDevice);
                monitorCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            monitorDevice = device;
                            AudioPlayback.setup(device.getDescriptor().sampleRate);
                        }
                        else {
                            monitorDevice = null;
                        }
                    }
                });
                int color = statusMap.get(device) <= 0 ? getResources().getColor(android.R.color.holo_red_light) : getResources().getColor(android.R.color.holo_green_light);
                ((TextView)convertView.findViewById(R.id.status_indicator)).setTextColor(color);
                return convertView;
            }
        };
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                deviceListAdapter.notifyDataSetInvalidated();
                handler.postDelayed(this, 500);
            }
        }, 500);

        ((ListView)findViewById(R.id.usbList)).setAdapter(deviceListAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
